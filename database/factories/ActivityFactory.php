<?php

namespace Database\Factories;

use App\Models\Activity;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Activity>
 */
class ActivityFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'coordinator_id' => User::factory()->state(['role' => 'koordinator']),
            'title' => fake()->sentence(3),
            'description' => fake()->paragraph(),
            'category' => 'Lingkungan',
            'location' => 'Makassar',
            'quota' => 10,
            'starts_at' => now()->addDays(2),
            'ends_at' => now()->addDays(2)->addHours(3),
            'registration_deadline' => now()->addDay(),
            'status' => 'open',
        ];
    }
}
