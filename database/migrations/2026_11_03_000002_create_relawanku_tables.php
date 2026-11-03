<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('activities', function (Blueprint $table) {
            $table->id();
            $table->foreignId('coordinator_id')->constrained('users')->restrictOnDelete();
            $table->string('title', 150);
            $table->text('description');
            $table->string('category', 60)->index();
            $table->string('location');
            $table->text('requirements')->nullable();
            $table->unsignedInteger('quota');
            $table->dateTime('starts_at');
            $table->dateTime('ends_at');
            $table->dateTime('registration_deadline');
            $table->enum('status', ['draft', 'open', 'closed', 'completed', 'cancelled'])->default('draft');
            $table->string('cover_path')->nullable();
            $table->timestamps();
            $table->softDeletes();
            $table->index(['status', 'starts_at']);
        });

        Schema::create('registrations', function (Blueprint $table) {
            $table->id();
            $table->foreignId('activity_id')->constrained()->restrictOnDelete();
            $table->foreignId('user_id')->constrained()->restrictOnDelete();
            $table->enum('status', ['registered', 'cancelled'])->default('registered');
            $table->dateTime('registered_at');
            $table->dateTime('cancelled_at')->nullable();
            $table->timestamps();
            $table->unique(['activity_id', 'user_id']);
            $table->index(['activity_id', 'status']);
        });

        Schema::create('tasks', function (Blueprint $table) {
            $table->id();
            $table->foreignId('activity_id')->constrained()->restrictOnDelete();
            $table->foreignId('assigned_to')->nullable()->constrained('users')->restrictOnDelete();
            $table->string('title', 150);
            $table->text('description')->nullable();
            $table->dateTime('due_at')->nullable();
            $table->enum('status', ['pending', 'in_progress', 'completed'])->default('pending');
            $table->timestamps();
            $table->softDeletes();
            $table->index(['activity_id', 'status']);
        });

        Schema::create('attendance_sessions', function (Blueprint $table) {
            $table->id();
            $table->foreignId('activity_id')->constrained()->restrictOnDelete();
            $table->foreignId('created_by')->constrained('users')->restrictOnDelete();
            $table->string('code_hash', 64);
            $table->dateTime('expires_at');
            $table->dateTime('closed_at')->nullable();
            $table->timestamps();
            $table->index(['activity_id', 'expires_at']);
        });

        Schema::create('attendances', function (Blueprint $table) {
            $table->id();
            $table->foreignId('registration_id')->unique()->constrained()->restrictOnDelete();
            $table->foreignId('attendance_session_id')->constrained()->restrictOnDelete();
            $table->dateTime('checked_in_at');
            $table->timestamps();
        });

        Schema::create('documentations', function (Blueprint $table) {
            $table->id();
            $table->foreignId('activity_id')->constrained()->restrictOnDelete();
            $table->foreignId('uploaded_by')->constrained('users')->restrictOnDelete();
            $table->string('image_path');
            $table->text('caption')->nullable();
            $table->timestamps();
            $table->softDeletes();
        });

        Schema::create('activity_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('actor_id')->nullable()->constrained('users')->nullOnDelete();
            $table->foreignId('activity_id')->nullable()->constrained()->nullOnDelete();
            $table->string('event', 80)->index();
            $table->json('properties')->nullable();
            $table->timestamps();
            $table->index(['activity_id', 'created_at']);
        });
    }

    public function down(): void
    {
        foreach (['activity_logs', 'documentations', 'attendances', 'attendance_sessions', 'tasks', 'registrations', 'activities'] as $table) {
            Schema::dropIfExists($table);
        }
    }
};
