<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasOne;

class Registration extends Model
{
    protected $fillable = ['activity_id', 'user_id', 'status', 'registered_at', 'cancelled_at'];

    protected function casts(): array
    {
        return ['registered_at' => 'immutable_datetime', 'cancelled_at' => 'immutable_datetime'];
    }

    public function activity(): BelongsTo
    {
        return $this->belongsTo(Activity::class)->withTrashed();
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class)->select(['id', 'name', 'email', 'phone']);
    }

    public function attendance(): HasOne
    {
        return $this->hasOne(Attendance::class);
    }
}
