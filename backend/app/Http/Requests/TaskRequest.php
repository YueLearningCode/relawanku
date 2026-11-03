<?php

namespace App\Http\Requests;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

class TaskRequest extends FormRequest
{
    public function authorize(): bool
    {
        return in_array($this->user()->role, ['koordinator', 'admin'], true);
    }

    public function rules(): array
    {
        return [
            'title' => [$this->isMethod('POST') ? 'required' : 'sometimes', 'required', 'string', 'max:150'],
            'description' => ['nullable', 'string', 'max:5000'],
            'assigned_to' => ['nullable', 'integer', Rule::exists('users', 'id')->where('role', 'relawan')->where('is_active', true)],
            'due_at' => ['nullable', 'date'],
            'status' => ['sometimes', 'required', Rule::in(['pending', 'in_progress', 'completed'])],
        ];
    }
}
