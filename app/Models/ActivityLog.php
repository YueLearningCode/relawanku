<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class ActivityLog extends Model
{
    protected $fillable = ['actor_id', 'activity_id', 'event', 'properties'];

    protected function casts(): array
    {
        return ['properties' => 'array'];
    }

    public static function record(User $actor, string $event, ?Activity $activity = null, array $properties = []): void
    {
        static::create(['actor_id' => $actor->id, 'activity_id' => $activity?->id, 'event' => $event, 'properties' => $properties]);
    }
}
