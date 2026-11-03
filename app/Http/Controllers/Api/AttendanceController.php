<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Attendance;
use App\Models\AttendanceSession;
use App\Support\ActivityAccess;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class AttendanceController extends Controller
{
    public function open(Request $request, Activity $activity): JsonResponse
    {
        $data = $request->validate(['duration_minutes' => ['required', 'integer', 'between:1,120']]);
        $result = DB::transaction(function () use ($request, $activity, $data) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            $this->ensureOngoing($locked);
            AttendanceSession::where('activity_id', $locked->id)->whereNull('closed_at')->update(['closed_at' => now()]);
            $code = strtoupper(bin2hex(random_bytes(4)));
            $session = AttendanceSession::create([
                'activity_id' => $locked->id,
                'created_by' => $request->user()->id,
                'code_hash' => hash('sha256', $code),
                'expires_at' => now()->addMinutes($data['duration_minutes'])->min($locked->ends_at),
            ]);
            ActivityLog::record($request->user(), 'attendance_session.opened', $locked, ['session_id' => $session->id]);

            return ['session' => $session, 'code' => $code];
        });

        return response()->json(['message' => 'Bagikan kode ini kepada peserta. Kode hanya ditampilkan sekali.', 'data' => $result], 201);
    }

    public function close(Request $request, Activity $activity, AttendanceSession $attendanceSession): JsonResponse
    {
        DB::transaction(function () use ($request, $activity, $attendanceSession) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            $session = AttendanceSession::where('activity_id', $locked->id)->lockForUpdate()->findOrFail($attendanceSession->id);
            $session->update(['closed_at' => $session->closed_at ?? now()]);
            ActivityLog::record($request->user(), 'attendance_session.closed', $locked, ['session_id' => $session->id]);
        });

        return response()->json(['message' => 'Sesi absensi ditutup.']);
    }

    public function checkIn(Request $request, Activity $activity): JsonResponse
    {
        abort_unless($request->user()->role === 'relawan', 403);
        $data = $request->validate(['code' => ['required', 'string', 'regex:/^[A-Fa-f0-9]{8}$/']]);
        $attendance = DB::transaction(function () use ($request, $activity, $data) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            $this->ensureOngoing($locked);
            $registration = $locked->activeRegistrations()->where('user_id', $request->user()->id)->first();
            abort_unless($registration, 403, 'Anda bukan peserta aktif kegiatan ini.');
            $session = AttendanceSession::where('activity_id', $locked->id)->whereNull('closed_at')->where('expires_at', '>', now())->where('code_hash', hash('sha256', strtoupper($data['code'])))->first();
            abort_unless($session, 422, 'Kode absensi tidak valid atau sudah kedaluwarsa.');
            abort_if($registration->attendance()->exists(), 409, 'Anda sudah melakukan absensi.');
            $attendance = Attendance::create(['registration_id' => $registration->id, 'attendance_session_id' => $session->id, 'checked_in_at' => now()]);
            ActivityLog::record($request->user(), 'attendance.checked_in', $locked, ['attendance_id' => $attendance->id]);

            return $attendance;
        });

        return response()->json(['message' => 'Absensi berhasil.', 'data' => $attendance], 201);
    }

    public function index(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::manage($request->user(), $activity);

        return response()->json($activity->registrations()->whereHas('attendance')->with(['user', 'attendance'])->orderBy('id')->paginate(20));
    }

    private function ensureOngoing(Activity $activity): void
    {
        abort_unless(in_array($activity->status, ['open', 'closed'], true) && now()->gte($activity->starts_at) && now()->lt($activity->ends_at), 409, 'Absensi hanya tersedia saat kegiatan berlangsung.');
    }
}
