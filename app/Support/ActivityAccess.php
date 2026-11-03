<?php

namespace App\Support;

use App\Models\Activity;
use App\Models\User;

class ActivityAccess
{
    public static function manage(User $user, Activity $activity): void
    {
        abort_unless($user->role === 'admin' || ($user->role === 'koordinator' && $activity->coordinator_id === $user->id), 403, 'Anda tidak berhak mengelola kegiatan ini.');
    }

    public static function view(User $user, Activity $activity): void
    {
        abort_if($activity->status === 'draft' && $user->role !== 'admin' && $activity->coordinator_id !== $user->id, 404);
    }

    public static function participant(User $user, Activity $activity): void
    {
        static::view($user, $activity);
        if ($user->role === 'admin' || ($user->role === 'koordinator' && $activity->coordinator_id === $user->id)) {
            return;
        }

        abort_unless($activity->registrations()->where('user_id', $user->id)->exists(), 403, 'Hanya peserta kegiatan yang dapat melihat data ini.');
    }
}
