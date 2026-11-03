<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Support\Facades\Storage;

class Activity extends Model
{
    use HasFactory, SoftDeletes;

    protected $attributes = ['status' => 'draft'];

    protected $fillable = ['coordinator_id', 'title', 'description', 'category', 'location', 'requirements', 'quota', 'starts_at', 'ends_at', 'registration_deadline', 'status', 'cover_path'];

    protected $appends = ['cover_url'];

    protected function casts(): array
    {
        return ['starts_at' => 'immutable_datetime', 'ends_at' => 'immutable_datetime', 'registration_deadline' => 'immutable_datetime', 'quota' => 'integer'];
    }

    public function getCoverUrlAttribute(): ?string
    {
        return $this->cover_path ? Storage::disk('public')->url($this->cover_path) : null;
    }

    public function coordinator(): BelongsTo
    {
        return $this->belongsTo(User::class, 'coordinator_id')->select(['id', 'name']);
    }

    public function registrations(): HasMany
    {
        return $this->hasMany(Registration::class);
    }

    public function activeRegistrations(): HasMany
    {
        return $this->registrations()->where('status', 'registered');
    }

    public function tasks(): HasMany
    {
        return $this->hasMany(Task::class);
    }

    public function documentations(): HasMany
    {
        return $this->hasMany(Documentation::class);
    }

    public function scopeVisibleTo(Builder $query, User $user): void
    {
        if ($user->role !== 'admin') {
            $query->where(function (Builder $q) use ($user) {
                $q->where('status', '!=', 'draft');
                if ($user->role === 'koordinator') {
                    $q->orWhere('coordinator_id', $user->id);
                }
            });
        }
    }
}
