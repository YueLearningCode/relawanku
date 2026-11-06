<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\ActivityRequest;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Support\ActivityAccess;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;
use Throwable;

class ActivityController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $filters = $request->validate([
            'search' => ['nullable', 'string', 'max:100'],
            'category' => ['nullable', 'string', 'max:60'],
            'status' => ['nullable', Rule::in(['draft', 'open', 'closed', 'completed', 'cancelled'])],
            'mine' => ['nullable', 'boolean'],
            'per_page' => ['nullable', 'integer', 'between:1,100'],
        ]);
        $query = Activity::visibleTo($request->user())->with('coordinator')->withCount('activeRegistrations');
        if (! empty($filters['search'])) {
            $query->where(function ($q) use ($filters) {
                $q->where('title', 'like', '%'.$filters['search'].'%')->orWhere('location', 'like', '%'.$filters['search'].'%');
            });
        }
        foreach (['category', 'status'] as $field) {
            if (! empty($filters[$field])) {
                $query->where($field, $filters[$field]);
            }
        }
        if ($request->boolean('mine')) {
            if ($request->user()->role === 'relawan') {
                $query->whereHas('registrations', fn ($q) => $q->where('user_id', $request->user()->id));
            } else {
                $query->where('coordinator_id', $request->user()->id);
            }
        }

        return response()->json($query->orderBy('starts_at')->orderBy('id')->paginate($filters['per_page'] ?? 15));
    }

    public function store(ActivityRequest $request): JsonResponse
    {
        $activity = DB::transaction(function () use ($request) {
            $data = $request->activityData();
            if ($request->user()->role === 'koordinator') {
                $data['coordinator_id'] = $request->user()->id;
            }
            $activity = Activity::create($data);
            ActivityLog::record($request->user(), 'activity.created', $activity);

            return $activity;
        });

        return response()->json(['message' => 'Kegiatan dibuat.', 'data' => $activity], 201);
    }

    public function show(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::view($request->user(), $activity);

        $activity->load('coordinator')->loadCount('activeRegistrations');
        $registration = null;
        if ($request->user()->role === 'relawan') {
            $registration = $activity->registrations()
                ->select(['id', 'activity_id', 'user_id', 'status', 'registered_at', 'cancelled_at'])
                ->where('user_id', $request->user()->id)
                ->with('attendance:id,registration_id,checked_in_at')
                ->first();
        }
        $activity->setAttribute('my_registration', $registration);

        return response()->json(['data' => $activity]);
    }

    public function update(ActivityRequest $request, Activity $activity): JsonResponse
    {
        $activity = DB::transaction(function () use ($request, $activity) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            $data = $request->activityData();
            $startsAt = CarbonImmutable::parse($data['starts_at'] ?? $locked->starts_at);
            $endsAt = CarbonImmutable::parse($data['ends_at'] ?? $locked->ends_at);
            $deadline = CarbonImmutable::parse($data['registration_deadline'] ?? $locked->registration_deadline);
            if ($endsAt->lte($startsAt) || $deadline->gt($startsAt)) {
                throw ValidationException::withMessages(['starts_at' => ['Jadwal tidak konsisten dengan data kegiatan terbaru.']]);
            }
            if (isset($data['quota']) && $data['quota'] < $locked->activeRegistrations()->count()) {
                throw ValidationException::withMessages(['quota' => ['Kuota tidak boleh kurang dari jumlah peserta terdaftar.']]);
            }
            $locked->update($data);
            ActivityLog::record($request->user(), 'activity.updated', $locked, ['fields' => array_keys($data)]);

            return $locked;
        });

        return response()->json(['message' => 'Kegiatan diperbarui.', 'data' => $activity]);
    }

    public function destroy(Request $request, Activity $activity): JsonResponse
    {
        $this->deleteActivities($request, [$activity->id]);

        return response()->json(['message' => 'Kegiatan diarsipkan.']);
    }

    public function bulkDestroy(Request $request): JsonResponse
    {
        $data = $request->validate(['ids' => ['required', 'array', 'min:1', 'max:100'], 'ids.*' => ['required', 'integer', 'distinct']]);
        $this->deleteActivities($request, $data['ids']);

        return response()->json(['message' => 'Kegiatan terpilih diarsipkan.']);
    }

    private function deleteActivities(Request $request, array $ids): void
    {
        DB::transaction(function () use ($request, $ids) {
            $activities = Activity::whereIn('id', $ids)->orderBy('id')->lockForUpdate()->get();
            abort_unless($activities->count() === count($ids), 404);
            foreach ($activities as $activity) {
                ActivityAccess::manage($request->user(), $activity);
            }
            foreach ($activities as $activity) {
                ActivityLog::record($request->user(), 'activity.deleted', $activity);
                $activity->delete();
            }
        });
    }

    public function cover(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::manage($request->user(), $activity);
        $request->validate(['image' => ['required', 'image', 'mimes:jpg,jpeg,png,webp', 'max:5120']]);
        $path = $request->file('image')->store('covers', 'public');
        try {
            DB::transaction(function () use ($request, $activity, $path) {
                $locked = Activity::lockForUpdate()->findOrFail($activity->id);
                ActivityAccess::manage($request->user(), $locked);
                $locked->update(['cover_path' => $path]);
                ActivityLog::record($request->user(), 'activity.cover_uploaded', $locked);
            });
        } catch (Throwable $e) {
            Storage::disk('public')->delete($path);
            throw $e;
        }

        return response()->json(['message' => 'Foto sampul diperbarui.', 'data' => $activity->fresh()]);
    }

    public function history(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::manage($request->user(), $activity);

        return response()->json(ActivityLog::where('activity_id', $activity->id)->latest('id')->paginate(20));
    }
}
