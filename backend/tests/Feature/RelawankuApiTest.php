<?php

namespace Tests\Feature;

use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Registration;
use App\Models\User;
use Database\Seeders\DemoSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Storage;
use Tests\TestCase;

class RelawankuApiTest extends TestCase
{
    use RefreshDatabase;

    private function user(string $role = 'relawan'): User
    {
        return User::factory()->create(['role' => $role]);
    }

    private function activity(?User $coordinator = null, array $attributes = []): Activity
    {
        return Activity::factory()->create([...$this->activityData(), 'coordinator_id' => ($coordinator ?? $this->user('koordinator'))->id, ...$attributes]);
    }

    private function activityData(array $attributes = []): array
    {
        $this->freezeTime();

        return [
            'title' => 'Bersih Pantai', 'description' => 'Membersihkan sampah pantai.', 'category' => 'Lingkungan', 'location' => 'Makassar', 'quota' => 10,
            'starts_at' => now()->addDays(2)->toISOString(), 'ends_at' => now()->addDays(2)->addHours(3)->toISOString(),
            'registration_deadline' => now()->addDay()->toISOString(), 'status' => 'open', ...$attributes,
        ];
    }

    private function register(User $user, Activity $activity): Registration
    {
        return Registration::create(['activity_id' => $activity->id, 'user_id' => $user->id, 'status' => 'registered', 'registered_at' => now()]);
    }

    public function test_registration_cannot_escalate_role_and_password_is_hidden(): void
    {
        $payload = ['name' => 'Relawan Baru', 'email' => 'baru@example.test', 'password' => 'Password123!', 'password_confirmation' => 'Password123!'];
        $this->postJson('/api/auth/register', [...$payload, 'role' => 'admin'])->assertUnprocessable()->assertJsonValidationErrors('role');
        $this->postJson('/api/auth/register', $payload)->assertCreated()->assertJsonPath('data.role', 'relawan')->assertJsonMissingPath('data.password');
        $this->assertNotSame('Password123!', User::first()->password);
    }

    public function test_real_bearer_login_and_logout_revoke_current_token(): void
    {
        $user = User::factory()->create(['password' => 'Password123!']);
        $login = $this->postJson('/api/auth/login', ['email' => $user->email, 'password' => 'Password123!', 'device_name' => 'phpunit'])->assertOk();
        $token = $login->json('data.token');
        $this->withToken($token)->getJson('/api/auth/me')->assertOk()->assertJsonPath('data.id', $user->id);
        $this->withToken($token)->postJson('/api/auth/logout')->assertOk();
        $this->assertDatabaseCount('personal_access_tokens', 0);
        // Reset guard cache to simulate a new HTTP request with the revoked token.
        app('auth')->forgetGuards();
        $this->withToken($token)->getJson('/api/auth/me')->assertUnauthorized();
    }

    public function test_guest_and_disabled_user_cannot_access_protected_routes(): void
    {
        $this->getJson('/api/activities')->assertUnauthorized();
        $this->actingAs(User::factory()->create(['is_active' => false]), 'sanctum')->getJson('/api/activities')->assertForbidden();
    }

    public function test_expired_token_is_rejected(): void
    {
        $token = $this->user()->createToken('expired', ['*'], now()->subMinute())->plainTextToken;
        $this->withToken($token)->getJson('/api/auth/me')->assertUnauthorized();
    }

    public function test_coordinator_crud_is_owned_and_has_transactional_history(): void
    {
        $coordinator = $this->user('koordinator');
        $id = $this->actingAs($coordinator, 'sanctum')->postJson('/api/activities', $this->activityData())->assertCreated()->assertJsonPath('data.coordinator_id', $coordinator->id)->json('data.id');
        $this->patchJson('/api/activities/'.$id, ['title' => 'Pantai Bersih'])->assertOk();
        $this->actingAs($this->user('koordinator'), 'sanctum')->patchJson('/api/activities/'.$id, ['title' => 'Tidak boleh'])->assertForbidden();
        $this->assertDatabaseHas('activities', ['id' => $id, 'title' => 'Pantai Bersih']);
        $this->assertDatabaseHas('activity_logs', ['activity_id' => $id, 'event' => 'activity.updated']);
        $this->actingAs($coordinator, 'sanctum')->deleteJson('/api/activities/'.$id)->assertOk();
        $this->assertSoftDeleted('activities', ['id' => $id]);
    }

    public function test_invalid_schedule_and_partial_schedule_update_are_rejected(): void
    {
        $coordinator = $this->user('koordinator');
        $this->actingAs($coordinator, 'sanctum')->postJson('/api/activities', $this->activityData(['ends_at' => now()->toISOString()]))->assertUnprocessable()->assertJsonValidationErrors('ends_at');
        $activity = $this->activity($coordinator);
        $this->patchJson('/api/activities/'.$activity->id, ['registration_deadline' => now()->addDays(5)->toISOString()])->assertUnprocessable()->assertJsonValidationErrors('registration_deadline');
    }

    public function test_relawan_cannot_create_activities_and_drafts_are_private(): void
    {
        $draft = $this->activity(attributes: ['status' => 'draft']);
        $this->actingAs($this->user(), 'sanctum')->postJson('/api/activities', $this->activityData())->assertForbidden();
        $this->getJson('/api/activities/'.$draft->id)->assertNotFound();
        $this->getJson('/api/activities')->assertOk()->assertJsonCount(0, 'data');
    }

    public function test_quota_duplicate_and_quota_reduction_are_checked(): void
    {
        $activity = $this->activity(attributes: ['quota' => 1]);
        $volunteer = $this->user();
        $url = '/api/activities/'.$activity->id.'/registrations';
        $this->actingAs($volunteer, 'sanctum')->postJson($url)->assertCreated();
        $this->postJson($url)->assertConflict();
        $this->actingAs($this->user(), 'sanctum')->postJson($url)->assertConflict();
        $this->assertDatabaseCount('registrations', 1);
        $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->patchJson('/api/activities/'.$activity->id, ['quota' => 0])->assertUnprocessable();
    }

    public function test_cancellation_unassigns_tasks_and_reregistration_reuses_row(): void
    {
        $activity = $this->activity();
        $volunteer = $this->user();
        $registration = $this->register($volunteer, $activity);
        $task = $activity->tasks()->create(['title' => 'Persiapan', 'assigned_to' => $volunteer->id]);
        $this->actingAs($volunteer, 'sanctum')->deleteJson('/api/activities/'.$activity->id.'/registration')->assertOk();
        $this->assertNull($task->fresh()->assigned_to);
        $this->postJson('/api/activities/'.$activity->id.'/registrations')->assertCreated()->assertJsonPath('data.id', $registration->id);
        $this->assertDatabaseCount('registrations', 1);
    }

    public function test_closed_or_past_deadline_registration_is_rejected(): void
    {
        $activity = $this->activity(attributes: ['registration_deadline' => now()->subMinute()]);
        $this->actingAs($this->user(), 'sanctum')->postJson('/api/activities/'.$activity->id.'/registrations')->assertConflict();
        $activity->update(['status' => 'closed', 'registration_deadline' => now()->addDay()]);
        $this->postJson('/api/activities/'.$activity->id.'/registrations')->assertConflict();
    }

    public function test_bulk_delete_rolls_back_when_one_activity_is_not_owned(): void
    {
        $owner = $this->user('koordinator');
        $owned = $this->activity($owner);
        $other = $this->activity();
        $this->actingAs($owner, 'sanctum')->deleteJson('/api/activities/bulk', ['ids' => [$owned->id, $other->id]])->assertForbidden();
        $this->assertNotSoftDeleted($owned);
        $this->assertNotSoftDeleted($other);
        $this->assertDatabaseCount('activity_logs', 0);
    }

    public function test_tasks_require_active_participant_and_are_scoped_to_activity(): void
    {
        $activity = $this->activity();
        $volunteer = $this->user();
        $url = '/api/activities/'.$activity->id.'/tasks';
        $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->postJson($url, ['title' => 'Logistik', 'assigned_to' => $volunteer->id])->assertUnprocessable();
        $this->register($volunteer, $activity);
        $taskId = $this->postJson($url, ['title' => 'Logistik', 'assigned_to' => $volunteer->id])->assertCreated()->json('data.id');
        $this->actingAs($volunteer, 'sanctum')->patchJson($url.'/'.$taskId.'/status', ['status' => 'completed'])->assertOk();
        $other = $this->activity();
        $this->getJson('/api/activities/'.$other->id.'/tasks/'.$taskId)->assertNotFound();
        $this->actingAs($this->user(), 'sanctum')->patchJson($url.'/'.$taskId.'/status', ['status' => 'completed'])->assertForbidden();
    }

    public function test_bulk_task_delete_cannot_delete_tasks_from_another_activity(): void
    {
        $owner = $this->user('koordinator');
        $activity = $this->activity($owner);
        $own = $activity->tasks()->create(['title' => 'Milik sendiri']);
        $other = $this->activity($owner)->tasks()->create(['title' => 'Kegiatan lain']);
        $this->actingAs($owner, 'sanctum')->deleteJson('/api/activities/'.$activity->id.'/tasks/bulk', ['ids' => [$own->id, $other->id]])->assertNotFound();
        $this->assertNotSoftDeleted($own);
        $this->assertNotSoftDeleted($other);
    }

    public function test_attendance_checks_code_expiry_membership_and_duplicates(): void
    {
        $activity = $this->activity(attributes: ['starts_at' => now()->subMinutes(5), 'ends_at' => now()->addHour(), 'registration_deadline' => now()->subHour()]);
        $volunteer = $this->user();
        $this->register($volunteer, $activity);
        $base = '/api/activities/'.$activity->id;
        $session = $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->postJson($base.'/attendance-sessions', ['duration_minutes' => 10])->assertCreated()->assertJsonMissingPath('data.session.code_hash');
        $code = $session->json('data.code');
        $this->actingAs($this->user(), 'sanctum')->postJson($base.'/attendances', ['code' => $code])->assertForbidden();
        $this->actingAs($volunteer, 'sanctum')->postJson($base.'/attendances', ['code' => '00000000'])->assertUnprocessable();
        $this->postJson($base.'/attendances', ['code' => $code])->assertCreated();
        $this->postJson($base.'/attendances', ['code' => $code])->assertConflict();
        $this->assertDatabaseCount('attendances', 1);
        $this->travel(11)->minutes();
        $this->postJson($base.'/attendances', ['code' => $code])->assertUnprocessable();
    }

    public function test_reopening_and_closing_sessions_invalidates_codes(): void
    {
        $activity = $this->activity(attributes: ['starts_at' => now()->subMinute(), 'ends_at' => now()->addHour()]);
        $volunteer = $this->user();
        $this->register($volunteer, $activity);
        $base = '/api/activities/'.$activity->id;
        $owner = User::find($activity->coordinator_id);
        $firstCode = $this->actingAs($owner, 'sanctum')->postJson($base.'/attendance-sessions', ['duration_minutes' => 10])->assertCreated()->json('data.code');
        $session = $this->postJson($base.'/attendance-sessions', ['duration_minutes' => 10])->assertCreated();
        $this->actingAs($volunteer, 'sanctum')->postJson($base.'/attendances', ['code' => $firstCode])->assertUnprocessable();
        $this->actingAs($owner, 'sanctum')->deleteJson($base.'/attendance-sessions/'.$session->json('data.session.id'))->assertOk();
        $this->actingAs($volunteer, 'sanctum')->postJson($base.'/attendances', ['code' => $session->json('data.code')])->assertUnprocessable();
    }

    public function test_documentation_upload_access_and_soft_delete(): void
    {
        Storage::fake('public');
        $activity = $this->activity();
        $volunteer = $this->user();
        $base = '/api/activities/'.$activity->id.'/documentations';
        $this->actingAs($volunteer, 'sanctum')->getJson($base)->assertForbidden();
        $this->register($volunteer, $activity);
        $this->postJson($base, ['image' => UploadedFile::fake()->image('foto.jpg')])->assertForbidden();
        $response = $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->postJson($base, ['image' => UploadedFile::fake()->image('foto.jpg'), 'caption' => 'Bersih pantai'])->assertCreated();
        Storage::disk('public')->assertExists($response->json('data.image_path'));
        $this->actingAs($volunteer, 'sanctum')->getJson($base)->assertOk()->assertJsonCount(1, 'data');
        $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->deleteJson($base.'/'.$response->json('data.id'))->assertOk();
        $this->assertSoftDeleted('documentations', ['id' => $response->json('data.id')]);
    }

    public function test_invalid_image_is_rejected(): void
    {
        $activity = $this->activity();
        $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->postJson('/api/activities/'.$activity->id.'/cover', ['image' => UploadedFile::fake()->create('script.php', 1, 'application/x-php')])->assertUnprocessable();
    }

    public function test_admin_accounts_and_self_protection(): void
    {
        $admin = $this->user('admin');
        $this->actingAs($this->user(), 'sanctum')->getJson('/api/users')->assertForbidden();
        $this->actingAs($admin, 'sanctum')->patchJson('/api/users/'.$admin->id, ['is_active' => false])->assertUnprocessable();
        $created = $this->postJson('/api/users', ['name' => 'Koordinator Baru', 'email' => 'koordinator.baru@example.test', 'password' => 'Password123!', 'password_confirmation' => 'Password123!', 'role' => 'koordinator'])->assertCreated();
        $this->patchJson('/api/users/'.$created->json('data.id'), ['is_active' => false])->assertOk();
        $this->assertDatabaseHas('users', ['id' => $created->json('data.id'), 'is_active' => false]);
    }

    public function test_demo_seeder_is_idempotent_and_preserves_passwords(): void
    {
        $this->seed(DemoSeeder::class);
        $user = User::where('email', 'relawan@gmail.com')->firstOrFail();
        $password = $user->password;
        $this->seed(DemoSeeder::class);
        $this->assertDatabaseCount('users', 3);
        $this->assertDatabaseCount('activities', 3);
        $this->assertDatabaseCount('registrations', 3);
        $this->assertDatabaseCount('tasks', 3);
        $this->assertSame($password, $user->fresh()->password);
    }

    public function test_history_and_dashboard_are_scoped_to_user(): void
    {
        $activity = $this->activity();
        $volunteer = $this->user();
        $this->register($volunteer, $activity);
        ActivityLog::record($volunteer, 'registration.created', $activity);
        ActivityLog::record(User::find($activity->coordinator_id), 'activity.created', $activity);
        $this->actingAs($volunteer, 'sanctum')->getJson('/api/dashboard')->assertOk()->assertJsonPath('data.registrations', 1);
        $this->getJson('/api/history')->assertOk()->assertJsonCount(1, 'data');
        $this->getJson('/api/activities/'.$activity->id.'/registrations')->assertForbidden();
    }

    public function test_quota_cannot_be_lowered_below_existing_active_registrations(): void
    {
        $activity = $this->activity(attributes: ['quota' => 3]);
        $this->register($this->user(), $activity);
        $this->register($this->user(), $activity);

        $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->patchJson('/api/activities/'.$activity->id, ['quota' => 1])->assertUnprocessable()->assertJsonValidationErrors('quota');

        $this->assertSame(3, $activity->fresh()->quota);
        $this->assertDatabaseCount('activity_logs', 0);
    }

    public function test_profile_cannot_change_role_or_account_status(): void
    {
        $volunteer = $this->user();

        $this->actingAs($volunteer, 'sanctum')->patchJson('/api/auth/me', ['role' => 'admin', 'is_active' => false])->assertUnprocessable()->assertJsonValidationErrors(['role', 'is_active']);

        $this->assertSame('relawan', $volunteer->fresh()->role);
        $this->assertTrue($volunteer->fresh()->is_active);
    }

    public function test_activity_changes_roll_back_if_history_cannot_be_written(): void
    {
        $coordinator = $this->user('koordinator');
        ActivityLog::creating(function () {
            throw new \RuntimeException('Simulated history storage failure');
        });

        $this->actingAs($coordinator, 'sanctum')->postJson('/api/activities', $this->activityData())->assertInternalServerError();

        $this->assertDatabaseCount('activities', 0);
        $this->assertDatabaseCount('activity_logs', 0);
    }

    public function test_successful_cover_upload_and_caption_update_return_public_urls(): void
    {
        Storage::fake('public');
        $activity = $this->activity();
        $base = '/api/activities/'.$activity->id;
        $this->actingAs(User::find($activity->coordinator_id), 'sanctum');

        $cover = $this->postJson($base.'/cover', ['image' => UploadedFile::fake()->image('cover.png')])->assertOk();
        Storage::disk('public')->assertExists($cover->json('data.cover_path'));
        $this->assertNotEmpty($cover->json('data.cover_url'));
        $photo = $this->postJson($base.'/documentations', ['image' => UploadedFile::fake()->image('kegiatan.png')])->assertCreated();
        $this->patchJson($base.'/documentations/'.$photo->json('data.id'), ['caption' => 'Kegiatan bersama relawan.'])->assertOk()->assertJsonPath('data.caption', 'Kegiatan bersama relawan.');
        $this->assertDatabaseHas('documentations', ['id' => $photo->json('data.id'), 'caption' => 'Kegiatan bersama relawan.']);
    }

    public function test_login_is_rate_limited_after_repeated_failures(): void
    {
        for ($attempt = 0; $attempt < 5; $attempt++) {
            $this->postJson('/api/auth/login', ['email' => 'salah@example.test', 'password' => 'Salah123!', 'device_name' => 'test'])->assertUnprocessable();
        }
        $this->postJson('/api/auth/login', ['email' => 'salah@example.test', 'password' => 'Salah123!', 'device_name' => 'test'])->assertTooManyRequests();
    }

    public function test_activity_detail_only_contains_the_callers_registration_and_fresh_status(): void
    {
        $activity = $this->activity();
        $volunteer = $this->user();
        $other = $this->user();
        $mine = $this->register($volunteer, $activity);
        $theirs = $this->register($other, $activity);
        $url = '/api/activities/'.$activity->id;

        $this->actingAs($volunteer, 'sanctum')->getJson($url)->assertOk()
            ->assertJsonPath('data.active_registrations_count', 2)
            ->assertJsonPath('data.my_registration.id', $mine->id)
            ->assertJsonPath('data.my_registration.user_id', $volunteer->id)
            ->assertJsonPath('data.my_registration.status', 'registered')
            ->assertJsonPath('data.my_registration.attendance', null)
            ->assertJsonMissingPath('data.registrations')
            ->assertJsonMissingPath('data.my_registration.user');
        $this->deleteJson($url.'/registration')->assertOk();
        $this->getJson($url)->assertOk()
            ->assertJsonPath('data.active_registrations_count', 1)
            ->assertJsonPath('data.my_registration.status', 'cancelled');
        $this->actingAs($other, 'sanctum')->getJson($url)->assertOk()
            ->assertJsonPath('data.my_registration.id', $theirs->id);
    }

    public function test_activity_detail_has_null_membership_for_unregistered_users_and_managers(): void
    {
        $activity = $this->activity();
        $this->register($this->user(), $activity);
        $url = '/api/activities/'.$activity->id;
        $this->actingAs($this->user(), 'sanctum')->getJson($url)->assertOk()
            ->assertJsonPath('data.my_registration', null);
        $this->actingAs(User::find($activity->coordinator_id), 'sanctum')->getJson($url)->assertOk()
            ->assertJsonPath('data.my_registration', null);
    }

    public function test_activity_detail_contains_attendance_without_private_session_fields(): void
    {
        $activity = $this->activity(attributes: [
            'starts_at' => now()->subMinute(), 'ends_at' => now()->addHour(),
            'registration_deadline' => now()->subHour(),
        ]);
        $volunteer = $this->user();
        $this->register($volunteer, $activity);
        $url = '/api/activities/'.$activity->id;
        $code = $this->actingAs(User::find($activity->coordinator_id), 'sanctum')
            ->postJson($url.'/attendance-sessions', ['duration_minutes' => 10])
            ->assertCreated()->json('data.code');
        $attendance = $this->actingAs($volunteer, 'sanctum')
            ->postJson($url.'/attendances', ['code' => $code])->assertCreated()->json('data');
        $this->getJson($url)->assertOk()
            ->assertJsonPath('data.my_registration.attendance.id', $attendance['id'])
            ->assertJsonPath('data.my_registration.attendance.checked_in_at', $attendance['checked_in_at'])
            ->assertJsonMissingPath('data.my_registration.attendance.attendance_session_id')
            ->assertJsonMissingPath('data.my_registration.attendance.code_hash');
    }
}
