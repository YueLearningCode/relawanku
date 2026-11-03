<?php

namespace Tests\Feature;

use GuzzleHttp\Client;
use PDO;
use PHPUnit\Framework\Attributes\Group;
use PHPUnit\Framework\TestCase;

#[Group('mysql-concurrency')]
class ConcurrentRegistrationTest extends TestCase
{
    public function test_only_one_of_two_concurrent_requests_gets_the_last_slot(): void
    {
        if (! getenv('TEST_API_URL') || ! getenv('TEST_API_URL_SECOND') || ! getenv('TEST_MYSQL_DATABASE')) {
            $this->markTestSkipped('Memerlukan dua server HTTP dan MySQL pengujian terpisah. Lihat docs/API.md.');
        }

        $base = rtrim(getenv('TEST_API_URL'), '/');
        $client = new Client(['base_uri' => $base.'/', 'http_errors' => false, 'timeout' => 15]);
        $suffix = bin2hex(random_bytes(6));
        $password = getenv('TEST_DEMO_PASSWORD') ?: 'RelawanDemo123!';
        $login = $client->post('auth/login', ['json' => ['email' => 'koordinator@relawanku.test', 'password' => $password, 'device_name' => 'Concurrency test']]);
        $this->assertSame(200, $login->getStatusCode());
        $coordinatorToken = json_decode($login->getBody(), true, flags: JSON_THROW_ON_ERROR)['data']['token'];

        $volunteerTokens = [];
        for ($index = 0; $index < 2; $index++) {
            $email = 'race.'.$suffix.'.'.$index.'@example.test';
            $registration = $client->post('auth/register', ['json' => ['name' => 'Uji Konkurensi '.$index, 'email' => $email, 'password' => 'Password123!', 'password_confirmation' => 'Password123!']]);
            $this->assertSame(201, $registration->getStatusCode());
            $login = $client->post('auth/login', ['json' => ['email' => $email, 'password' => 'Password123!', 'device_name' => 'Concurrency test']]);
            $this->assertSame(200, $login->getStatusCode());
            $volunteerTokens[] = json_decode($login->getBody(), true, flags: JSON_THROW_ON_ERROR)['data']['token'];
        }

        $created = $client->post('activities', ['headers' => ['Authorization' => 'Bearer '.$coordinatorToken], 'json' => [
            'title' => '[TEST] Satu Slot '.$suffix, 'description' => 'Pengujian dua request bersamaan.', 'category' => 'Pengujian', 'location' => 'Lokal', 'quota' => 1, 'status' => 'open',
            'starts_at' => gmdate('c', time() + 172800), 'ends_at' => gmdate('c', time() + 180000), 'registration_deadline' => gmdate('c', time() + 86400),
        ]]);
        $this->assertSame(201, $created->getStatusCode());
        $activityId = json_decode($created->getBody(), true, flags: JSON_THROW_ON_ERROR)['data']['id'];
        $pdo = new PDO(
            'mysql:host='.(getenv('TEST_MYSQL_HOST') ?: '127.0.0.1').';port='.(getenv('TEST_MYSQL_PORT') ?: '3306').';dbname='.getenv('TEST_MYSQL_DATABASE'),
            getenv('TEST_MYSQL_USERNAME') ?: 'root', getenv('TEST_MYSQL_PASSWORD') ?: '', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION],
        );
        $multi = curl_multi_init();
        $handles = [];
        try {
            $pdo->beginTransaction();
            $lock = $pdo->prepare('SELECT id FROM activities WHERE id = ? FOR UPDATE');
            $lock->execute([$activityId]);
            $this->assertSame((int) $activityId, (int) $lock->fetchColumn());

            foreach ([$base, rtrim(getenv('TEST_API_URL_SECOND'), '/')] as $index => $url) {
                $handle = curl_init($url.'/activities/'.$activityId.'/registrations');
                curl_setopt_array($handle, [CURLOPT_POST => true, CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 15, CURLOPT_HTTPHEADER => ['Accept: application/json', 'Authorization: Bearer '.$volunteerTokens[$index]]]);
                curl_multi_add_handle($multi, $handle);
                $handles[] = $handle;
            }

            $waiting = $pdo->prepare('SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON w.REQUESTING_ENGINE_LOCK_ID = l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA = ? AND l.OBJECT_NAME = ?');
            $deadline = microtime(true) + 8;
            $waiters = 0;
            do {
                curl_multi_exec($multi, $running);
                curl_multi_select($multi, 0.02);
                $waiting->execute([getenv('TEST_MYSQL_DATABASE'), 'activities']);
                $waiters = (int) $waiting->fetchColumn();
            } while ($waiters < 2 && microtime(true) < $deadline);

            // Both HTTP workers must actually be waiting on the same database row.
            $this->assertGreaterThanOrEqual(2, $waiters, 'Kedua request harus benar-benar menunggu lock MySQL yang sama.');
            $pdo->commit();
            do {
                curl_multi_exec($multi, $running);
                if ($running) {
                    curl_multi_select($multi, 0.05);
                }
            } while ($running);

            $statuses = array_map(fn ($handle): int => curl_getinfo($handle, CURLINFO_RESPONSE_CODE), $handles);
            sort($statuses);
            $this->assertSame([201, 409], $statuses, implode('\n', array_map(fn ($handle): string => curl_multi_getcontent($handle), $handles)));
            $count = $pdo->prepare('SELECT COUNT(*) FROM registrations WHERE activity_id = ? AND status = ?');
            $count->execute([$activityId, 'registered']);
            $this->assertSame(1, (int) $count->fetchColumn());
        } finally {
            if ($pdo->inTransaction()) {
                $pdo->rollBack();
            }
            foreach ($handles as $handle) {
                curl_multi_remove_handle($multi, $handle);
            }
            curl_multi_close($multi);
            $client->delete('activities/'.$activityId, ['headers' => ['Authorization' => 'Bearer '.$coordinatorToken]]);
        }
    }
}
