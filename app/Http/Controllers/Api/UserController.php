<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Password;
use Illuminate\Validation\ValidationException;

class UserController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $this->admin($request);
        $data = $request->validate(['search' => ['nullable', 'string', 'max:100'], 'role' => ['nullable', Rule::in(['relawan', 'koordinator', 'admin'])]]);
        $query = User::query();
        if (! empty($data['search'])) {
            $query->where(fn ($q) => $q->where('name', 'like', '%'.$data['search'].'%')->orWhere('email', 'like', '%'.$data['search'].'%'));
        }
        if (! empty($data['role'])) {
            $query->where('role', $data['role']);
        }

        return response()->json($query->orderBy('id')->paginate(20));
    }

    public function store(Request $request): JsonResponse
    {
        $this->admin($request);
        $data = $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'email' => ['required', 'email', 'lowercase', 'max:255', 'unique:users'],
            'password' => ['required', 'confirmed', Password::min(8)->letters()->numbers()],
            'role' => ['required', Rule::in(['relawan', 'koordinator', 'admin'])],
            'phone' => ['nullable', 'string', 'max:25'],
        ]);
        $user = DB::transaction(function () use ($request, $data) {
            $user = User::create($data);
            ActivityLog::record($request->user(), 'user.created', properties: ['user_id' => $user->id, 'role' => $user->role]);

            return $user;
        });

        return response()->json(['message' => 'Akun dibuat.', 'data' => $user], 201);
    }

    public function update(Request $request, User $user): JsonResponse
    {
        $this->admin($request);
        $data = $request->validate([
            'name' => ['sometimes', 'required', 'string', 'max:100'],
            'email' => ['sometimes', 'required', 'email', 'lowercase', 'max:255', Rule::unique('users')->ignore($user->id)],
            'phone' => ['nullable', 'string', 'max:25'],
            'role' => ['sometimes', 'required', Rule::in(['relawan', 'koordinator', 'admin'])],
            'is_active' => ['sometimes', 'required', 'boolean'],
        ]);
        $user = DB::transaction(function () use ($request, $user, $data) {
            $user = User::lockForUpdate()->findOrFail($user->id);
            if ($user->id === $request->user()->id && (isset($data['role']) && $data['role'] !== 'admin' || isset($data['is_active']) && ! $data['is_active'])) {
                throw ValidationException::withMessages(['role' => ['Admin tidak dapat menurunkan peran atau menonaktifkan akun sendiri.']]);
            }
            if (isset($data['role']) && $data['role'] !== $user->role) {
                if (Activity::withTrashed()->where('coordinator_id', $user->id)->exists() || $user->registrations()->exists()) {
                    throw ValidationException::withMessages(['role' => ['Peran akun yang sudah memiliki kegiatan atau pendaftaran tidak dapat diubah.']]);
                }
            }
            $user->update($data);
            if (! $user->is_active || array_key_exists('role', $data)) {
                $user->tokens()->delete();
            }
            ActivityLog::record($request->user(), 'user.updated', properties: ['user_id' => $user->id, 'fields' => array_keys($data)]);

            return $user;
        });

        return response()->json(['message' => 'Akun diperbarui.', 'data' => $user]);
    }

    private function admin(Request $request): void
    {
        abort_unless($request->user()->role === 'admin', 403, 'Akses hanya untuk admin.');
    }
}
