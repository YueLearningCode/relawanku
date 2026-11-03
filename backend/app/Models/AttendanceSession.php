<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AttendanceSession extends Model
{
    protected $fillable = ['activity_id', 'created_by', 'code_hash', 'expires_at', 'closed_at'];

    protected $hidden = ['code_hash'];

    protected function casts(): array
    {
        return ['expires_at' => 'immutable_datetime', 'closed_at' => 'immutable_datetime'];
    }
}
