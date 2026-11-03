<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;

class EnsureActiveUser
{
    public function handle(Request $request, Closure $next): mixed
    {
        abort_unless($request->user()?->is_active, 403, 'Akun Anda dinonaktifkan.');

        return $next($request);
    }
}
