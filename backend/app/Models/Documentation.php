<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Support\Facades\Storage;

class Documentation extends Model
{
    use SoftDeletes;

    protected $fillable = ['activity_id', 'uploaded_by', 'image_path', 'caption'];

    protected $appends = ['image_url'];

    public function getImageUrlAttribute(): string
    {
        return Storage::disk('public')->url($this->image_path);
    }
}
