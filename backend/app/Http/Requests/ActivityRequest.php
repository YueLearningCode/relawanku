<?php

namespace App\Http\Requests;

use Carbon\CarbonImmutable;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Validator;

class ActivityRequest extends FormRequest
{
    public function authorize(): bool
    {
        return in_array($this->user()->role, ['koordinator', 'admin'], true);
    }

    public function rules(): array
    {
        $required = $this->isMethod('POST') ? 'required' : 'sometimes';

        return [
            'title' => [$required, 'required', 'string', 'max:150'],
            'description' => [$required, 'required', 'string', 'max:10000'],
            'category' => [$required, 'required', 'string', 'max:60'],
            'location' => [$required, 'required', 'string', 'max:255'],
            'requirements' => ['nullable', 'string', 'max:5000'],
            'quota' => [$required, 'required', 'integer', 'min:1', 'max:100000'],
            'starts_at' => [$required, 'required', 'date'],
            'ends_at' => [$required, 'required', 'date'],
            'registration_deadline' => [$required, 'required', 'date'],
            'status' => ['sometimes', 'required', Rule::in(['draft', 'open', 'closed', 'completed', 'cancelled'])],
            'coordinator_id' => $this->user()->role === 'admin'
                ? [$required, 'integer', Rule::exists('users', 'id')->where('role', 'koordinator')->where('is_active', true)]
                : ['prohibited'],
        ];
    }

    public function after(): array
    {
        return [function (Validator $validator) {
            if ($validator->errors()->isNotEmpty()) {
                return;
            }

            $activity = $this->route('activity');
            $start = CarbonImmutable::parse($this->input('starts_at', $activity?->starts_at));
            $end = CarbonImmutable::parse($this->input('ends_at', $activity?->ends_at));
            $deadline = CarbonImmutable::parse($this->input('registration_deadline', $activity?->registration_deadline));

            if ($end->lessThanOrEqualTo($start)) {
                $validator->errors()->add('ends_at', 'Waktu selesai harus setelah waktu mulai.');
            }
            if ($deadline->greaterThan($start)) {
                $validator->errors()->add('registration_deadline', 'Batas pendaftaran tidak boleh melewati waktu mulai.');
            }
        }];
    }

    public function activityData(): array
    {
        $data = $this->validated();
        foreach (['starts_at', 'ends_at', 'registration_deadline'] as $field) {
            if (isset($data[$field])) {
                $data[$field] = CarbonImmutable::parse($data[$field])->utc();
            }
        }

        return $data;
    }
}
