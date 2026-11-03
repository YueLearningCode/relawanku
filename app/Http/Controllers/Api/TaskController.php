<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Http\Requests\TaskRequest;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Task;
use App\Support\ActivityAccess;
use Carbon\CarbonImmutable;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class TaskController extends Controller
{
    public function index(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::participant($request->user(), $activity);
        $query = $activity->tasks()->with('assignee');
        if ($request->user()->role === 'relawan') {
            $query->where('assigned_to', $request->user()->id);
        }

        return response()->json($query->latest('id')->paginate(20));
    }

    public function show(Request $request, Activity $activity, Task $task): JsonResponse
    {
        abort_unless($task->activity_id === $activity->id, 404);
        ActivityAccess::participant($request->user(), $activity);
        if ($request->user()->role === 'relawan') {
            abort_unless($task->assigned_to === $request->user()->id, 403);
        }

        return response()->json(['data' => $task->load('assignee')]);
    }

    public function store(TaskRequest $request, Activity $activity): JsonResponse
    {
        $task = $this->save($request, $activity);

        return response()->json(['message' => 'Tugas dibuat.', 'data' => $task], 201);
    }

    public function update(TaskRequest $request, Activity $activity, Task $task): JsonResponse
    {
        $task = $this->save($request, $activity, $task);

        return response()->json(['message' => 'Tugas diperbarui.', 'data' => $task]);
    }

    private function save(TaskRequest $request, Activity $activity, ?Task $task = null): Task
    {
        return DB::transaction(function () use ($request, $activity, $task) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            if ($task) {
                $task = $locked->tasks()->lockForUpdate()->findOrFail($task->id);
            }
            $data = $request->validated();
            $assignedTo = array_key_exists('assigned_to', $data) ? $data['assigned_to'] : $task?->assigned_to;
            if ($assignedTo && ! $locked->activeRegistrations()->where('user_id', $assignedTo)->exists()) {
                throw ValidationException::withMessages(['assigned_to' => ['Penanggung jawab harus peserta aktif kegiatan ini.']]);
            }
            if (! empty($data['due_at'])) {
                $data['due_at'] = CarbonImmutable::parse($data['due_at'])->utc();
                if ($data['due_at']->gt($locked->ends_at)) {
                    throw ValidationException::withMessages(['due_at' => ['Batas tugas tidak boleh melewati akhir kegiatan.']]);
                }
            }
            $event = $task ? 'task.updated' : 'task.created';
            if ($task) {
                $task->update($data);
            } else {
                $task = $locked->tasks()->create($data);
            }
            ActivityLog::record($request->user(), $event, $locked, ['task_id' => $task->id]);

            return $task;
        });
    }

    public function status(Request $request, Activity $activity, Task $task): JsonResponse
    {
        $data = $request->validate(['status' => ['required', Rule::in(['pending', 'in_progress', 'completed'])]]);
        $task = DB::transaction(function () use ($request, $activity, $task, $data) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            $task = $locked->tasks()->lockForUpdate()->findOrFail($task->id);
            abort_unless($request->user()->role === 'relawan' && $task->assigned_to === $request->user()->id && $locked->activeRegistrations()->where('user_id', $request->user()->id)->exists(), 403);
            $task->update($data);
            ActivityLog::record($request->user(), 'task.status_updated', $locked, ['task_id' => $task->id, 'status' => $data['status']]);

            return $task;
        });

        return response()->json(['message' => 'Status tugas diperbarui.', 'data' => $task]);
    }

    public function destroy(Request $request, Activity $activity, Task $task): JsonResponse
    {
        return $this->deleteTasks($request, $activity, [$task->id]);
    }

    public function bulkDestroy(Request $request, Activity $activity): JsonResponse
    {
        $data = $request->validate(['ids' => ['required', 'array', 'min:1', 'max:100'], 'ids.*' => ['required', 'integer', 'distinct']]);

        return $this->deleteTasks($request, $activity, $data['ids']);
    }

    private function deleteTasks(Request $request, Activity $activity, array $ids): JsonResponse
    {
        DB::transaction(function () use ($request, $activity, $ids) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            $tasks = $locked->tasks()->whereIn('id', $ids)->lockForUpdate()->get();
            abort_unless($tasks->count() === count($ids), 404);
            foreach ($tasks as $task) {
                ActivityLog::record($request->user(), 'task.deleted', $locked, ['task_id' => $task->id]);
                $task->delete();
            }
        });

        return response()->json(['message' => 'Tugas terpilih diarsipkan.']);
    }
}
