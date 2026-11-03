<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Registration;
use App\Models\Task;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

class DashboardController extends Controller
{
    public function __invoke(Request $request): JsonResponse
    {
        $user = $request->user();
        if ($user->role === 'relawan') {
            return response()->json(['data' => [
                'registrations' => Registration::where('user_id', $user->id)->where('status', 'registered')->count(),
                'attendances' => Registration::where('user_id', $user->id)->whereHas('attendance')->count(),
                'pending_tasks' => Task::where('assigned_to', $user->id)->where('status', '!=', 'completed')->whereIn('activity_id', Activity::select('id'))->count(),
            ]]);
        }

        $activities = Activity::query();
        if ($user->role === 'koordinator') {
            $activities->where('coordinator_id', $user->id);
        }
        $ids = (clone $activities)->select('id');
        $data = [
            'activities' => (clone $activities)->count(),
            'active_activities' => (clone $activities)->whereIn('status', ['open', 'closed'])->where('ends_at', '>', now())->count(),
            'registrations' => Registration::whereIn('activity_id', clone $ids)->where('status', 'registered')->count(),
            'attendances' => Registration::whereIn('activity_id', clone $ids)->whereHas('attendance')->count(),
        ];
        if ($user->role === 'admin') {
            $data['users'] = User::count();
        }

        return response()->json(['data' => $data]);
    }

    public function history(Request $request): JsonResponse
    {
        return response()->json(ActivityLog::where('actor_id', $request->user()->id)->latest('id')->paginate(20));
    }
}
