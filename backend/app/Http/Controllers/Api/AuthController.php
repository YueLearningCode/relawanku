<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\Rule;
use Illuminate\Validation\Rules\Password;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:100'],
            'email' => ['required', 'email', 'max:255', 'lowercase', 'unique:users'],
            'password' => ['required', 'confirmed', Password::min(8)->letters()->numbers()],
            'phone' => ['nullable', 'string', 'max:25'],
            'role' => ['prohibited'],
        ]);
        $user = User::create([...collect($data)->except('password_confirmation')->all(), 'role' => 'relawan']);

        return response()->json(['message' => 'Pendaftaran berhasil.', 'data' => $user], 201);
    }

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email'],
            'password' => ['required', 'string'],
            'device_name' => ['required', 'string', 'max:100'],
        ]);
        $user = User::where('email', $data['email'])->first();
        if (! $user || ! Hash::check($data['password'], $user->password) || ! $user->is_active) {
            throw ValidationException::withMessages(['email' => ['Email atau kata sandi tidak sesuai, atau akun dinonaktifkan.']]);
        }

        $expiresAt = now()->addDays(7);
        $token = $user->createToken($data['device_name'], ['*'], $expiresAt);

        return response()->json(['message' => 'Login berhasil.', 'data' => ['user' => $user, 'token' => $token->plainTextToken, 'token_type' => 'Bearer', 'expires_at' => $expiresAt->toISOString()]]);
    }

    public function me(Request $request): JsonResponse
    {
        return response()->json(['data' => $request->user()]);
    }

    public function update(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['sometimes', 'required', 'string', 'max:100'],
            'email' => ['sometimes', 'required', 'email', 'lowercase', 'max:255', Rule::unique('users')->ignore($request->user()->id)],
            'phone' => ['nullable', 'string', 'max:25'],
            'bio' => ['nullable', 'string', 'max:2000'],
            'role' => ['prohibited'],
            'is_active' => ['prohibited'],
        ]);
        $request->user()->update($data);

        return response()->json(['message' => 'Profil diperbarui.', 'data' => $request->user()->fresh()]);
    }

    public function logout(Request $request): JsonResponse
    {
        $request->user()->currentAccessToken()?->delete();

        return response()->json(['message' => 'Logout berhasil.']);
    }
}
