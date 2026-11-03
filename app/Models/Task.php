<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\SoftDeletes;

class Task extends Model
{
    use SoftDeletes;

    protected $attributes = ['status' => 'pending'];

    protected $fillable = ['activity_id', 'assigned_to', 'title', 'description', 'due_at', 'status'];

    protected function casts(): array
    {
        return ['due_at' => 'immutable_datetime'];
    }

    public function assignee(): BelongsTo
    {
        return $this->belongsTo(User::class, 'assigned_to')->select(['id', 'name']);
    }
}
