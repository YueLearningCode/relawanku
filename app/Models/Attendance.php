<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class Attendance extends Model
{
    protected $fillable = ['registration_id', 'attendance_session_id', 'checked_in_at'];

    protected function casts(): array
    {
        return ['checked_in_at' => 'immutable_datetime'];
    }
}
