<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Registration;
use App\Support\ActivityAccess;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

class RegistrationController extends Controller
{
    public function mine(Request $request): JsonResponse
    {
        return response()->json(Registration::where('user_id', $request->user()->id)->with(['activity', 'attendance'])->latest('id')->paginate(15));
    }

    public function index(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::manage($request->user(), $activity);

        return response()->json($activity->registrations()->with(['user', 'attendance'])->latest('id')->paginate(20));
    }

    public function store(Request $request, Activity $activity): JsonResponse
    {
        abort_unless($request->user()->role === 'relawan', 403, 'Pendaftaran hanya untuk relawan.');
        $registration = DB::transaction(function () use ($request, $activity) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            abort_unless($locked->status === 'open' && now()->lte($locked->registration_deadline) && now()->lt($locked->starts_at), 409, 'Pendaftaran kegiatan tidak dibuka.');
            $registration = $locked->registrations()->where('user_id', $request->user()->id)->first();
            abort_if($registration?->status === 'registered', 409, 'Anda sudah terdaftar.');
            abort_if($locked->activeRegistrations()->count() >= $locked->quota, 409, 'Kuota kegiatan penuh.');
            $registration = $locked->registrations()->updateOrCreate(
                ['user_id' => $request->user()->id],
                ['status' => 'registered', 'registered_at' => now(), 'cancelled_at' => null],
            );
            ActivityLog::record($request->user(), 'registration.created', $locked, ['registration_id' => $registration->id]);

            return $registration;
        });

        return response()->json(['message' => 'Berhasil mendaftar kegiatan.', 'data' => $registration], 201);
    }

    public function cancelMine(Request $request, Activity $activity): JsonResponse
    {
        $registration = $activity->registrations()->where('user_id', $request->user()->id)->firstOrFail();

        return $this->destroy($request, $activity, $registration);
    }

    public function destroy(Request $request, Activity $activity, Registration $registration): JsonResponse
    {
        DB::transaction(function () use ($request, $activity, $registration) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            $registration = $locked->registrations()->lockForUpdate()->findOrFail($registration->id);
            if ($registration->user_id !== $request->user()->id) {
                ActivityAccess::manage($request->user(), $locked);
            }
            abort_unless($registration->status === 'registered', 409, 'Pendaftaran sudah dibatalkan.');
            abort_if(now()->gte($locked->starts_at) || $registration->attendance()->exists(), 409, 'Pendaftaran yang sudah dimulai atau memiliki absensi tidak dapat dibatalkan.');
            $registration->update(['status' => 'cancelled', 'cancelled_at' => now()]);
            $locked->tasks()->where('assigned_to', $registration->user_id)->update(['assigned_to' => null]);
            ActivityLog::record($request->user(), 'registration.cancelled', $locked, ['registration_id' => $registration->id]);
        });

        return response()->json(['message' => 'Pendaftaran dibatalkan.']);
    }
}
