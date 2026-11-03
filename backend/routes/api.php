<?php

use App\Http\Controllers\Api\ActivityController;
use App\Http\Controllers\Api\AttendanceController;
use App\Http\Controllers\Api\AuthController;
use App\Http\Controllers\Api\DashboardController;
use App\Http\Controllers\Api\DocumentationController;
use App\Http\Controllers\Api\RegistrationController;
use App\Http\Controllers\Api\TaskController;
use App\Http\Controllers\Api\UserController;
use Illuminate\Support\Facades\Route;

Route::get('health', fn () => response()->json(['message' => 'Relawanku API siap.']));
Route::post('auth/register', [AuthController::class, 'register'])->middleware('throttle:register');
Route::post('auth/login', [AuthController::class, 'login'])->middleware('throttle:login');

Route::middleware(['auth:sanctum', 'active', 'throttle:api'])->group(function () {
    Route::get('auth/me', [AuthController::class, 'me']);
    Route::patch('auth/me', [AuthController::class, 'update']);
    Route::post('auth/logout', [AuthController::class, 'logout']);
    Route::get('dashboard', DashboardController::class);
    Route::get('history', [DashboardController::class, 'history']);
    Route::get('my/registrations', [RegistrationController::class, 'mine']);

    Route::delete('activities/bulk', [ActivityController::class, 'bulkDestroy']);
    Route::apiResource('activities', ActivityController::class);
    Route::post('activities/{activity}/cover', [ActivityController::class, 'cover']);
    Route::get('activities/{activity}/history', [ActivityController::class, 'history']);
    Route::get('activities/{activity}/registrations', [RegistrationController::class, 'index']);
    Route::post('activities/{activity}/registrations', [RegistrationController::class, 'store']);
    Route::delete('activities/{activity}/registration', [RegistrationController::class, 'cancelMine']);
    Route::delete('activities/{activity}/registrations/{registration}', [RegistrationController::class, 'destroy']);

    Route::delete('activities/{activity}/tasks/bulk', [TaskController::class, 'bulkDestroy']);
    Route::patch('activities/{activity}/tasks/{task}/status', [TaskController::class, 'status']);
    Route::apiResource('activities.tasks', TaskController::class);

    Route::post('activities/{activity}/attendance-sessions', [AttendanceController::class, 'open']);
    Route::delete('activities/{activity}/attendance-sessions/{attendanceSession}', [AttendanceController::class, 'close']);
    Route::post('activities/{activity}/attendances', [AttendanceController::class, 'checkIn'])->middleware('throttle:attendance');
    Route::get('activities/{activity}/attendances', [AttendanceController::class, 'index']);

    Route::apiResource('activities.documentations', DocumentationController::class)->except('show');
    Route::apiResource('users', UserController::class)->only(['index', 'store', 'update']);
});
