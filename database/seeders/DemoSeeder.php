<?php

namespace Database\Seeders;

use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Registration;
use App\Models\Task;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\DB;
use RuntimeException;

class DemoSeeder extends Seeder
{
    public function run(): void
    {
        if (! app()->environment(['local', 'testing'])) {
            throw new RuntimeException('DemoSeeder hanya boleh dijalankan di lingkungan local/testing.');
        }

        DB::transaction(function () {
            $password = env('DEMO_PASSWORD', 'RelawanDemo123!');
            foreach (['admin' => 'Yue', 'koordinator' => 'Koordinator Demo', 'relawan' => 'Relawan Demo'] as $role => $name) {
                // Akun yang sudah ada tidak diganti password, nama, atau perannya.
                $user = User::firstOrCreate(['email' => $role.'@gmail.com'], ['name' => $name, 'password' => $password, 'role' => $role]);
                if ($user->role !== $role || ! $user->is_active) {
                    throw new RuntimeException('Email demo sudah dipakai akun nonaktif atau dengan peran berbeda. Seeder dibatalkan.');
                }
            }
            $coordinator = User::where('email', 'koordinator@gmail.com')->firstOrFail();
            $volunteer = User::where('email', 'relawan@gmail.com')->firstOrFail();
            if ($coordinator->role !== 'koordinator' || $volunteer->role !== 'relawan') {
                throw new RuntimeException('Email demo sudah digunakan oleh akun dengan peran berbeda. Seeder dibatalkan.');
            }

            $examples = [
                ['title' => '[DEMO] Bersih Pantai', 'category' => 'Lingkungan', 'location' => 'Pantai Losari, Makassar', 'quota' => 30],
                ['title' => '[DEMO] Kelas Berbagi', 'category' => 'Pendidikan', 'location' => 'Rumah Belajar Komunitas, Makassar', 'quota' => 15],
                ['title' => '[DEMO] Dapur Peduli', 'category' => 'Sosial', 'location' => 'Dapur Komunitas, Makassar', 'quota' => 20],
            ];
            foreach ($examples as $index => $example) {
                $start = now()->addDays(7 + $index)->setTime(1, 0);
                $activity = Activity::withTrashed()->firstOrCreate(
                    ['coordinator_id' => $coordinator->id, 'title' => $example['title']],
                    [...$example, 'description' => 'Data contoh lokal untuk pengujian API Relawanku.', 'requirements' => 'Bersedia mengikuti arahan koordinator.', 'starts_at' => $start, 'ends_at' => $start->copy()->addHours(4), 'registration_deadline' => $start->copy()->subDay(), 'status' => 'open'],
                );
                if ($activity->trashed()) {
                    continue;
                }
                Registration::firstOrCreate(['activity_id' => $activity->id, 'user_id' => $volunteer->id], ['status' => 'registered', 'registered_at' => now()]);
                Task::withTrashed()->firstOrCreate(['activity_id' => $activity->id, 'title' => 'Persiapan perlengkapan'], ['assigned_to' => $volunteer->id, 'description' => 'Siapkan perlengkapan bersama koordinator.', 'due_at' => $start, 'status' => 'pending']);
                ActivityLog::firstOrCreate(['activity_id' => $activity->id, 'actor_id' => $coordinator->id, 'event' => 'demo.seeded'], ['properties' => ['source' => 'DemoSeeder']]);
            }
        });
    }
}
