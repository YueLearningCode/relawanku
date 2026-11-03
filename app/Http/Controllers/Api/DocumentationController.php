<?php

namespace App\Http\Controllers\Api;

use App\Http\Controllers\Controller;
use App\Models\Activity;
use App\Models\ActivityLog;
use App\Models\Documentation;
use App\Support\ActivityAccess;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Throwable;

class DocumentationController extends Controller
{
    public function index(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::participant($request->user(), $activity);

        return response()->json($activity->documentations()->latest('id')->paginate(20));
    }

    public function store(Request $request, Activity $activity): JsonResponse
    {
        ActivityAccess::manage($request->user(), $activity);
        $data = $request->validate([
            'image' => ['required', 'image', 'mimes:jpg,jpeg,png,webp', 'max:5120'],
            'caption' => ['nullable', 'string', 'max:2000'],
        ]);
        $path = $request->file('image')->store('documentations', 'public');
        try {
            $documentation = DB::transaction(function () use ($request, $activity, $data, $path) {
                $locked = Activity::lockForUpdate()->findOrFail($activity->id);
                ActivityAccess::manage($request->user(), $locked);
                $documentation = $locked->documentations()->create(['uploaded_by' => $request->user()->id, 'image_path' => $path, 'caption' => $data['caption'] ?? null]);
                ActivityLog::record($request->user(), 'documentation.created', $locked, ['documentation_id' => $documentation->id]);

                return $documentation;
            });
        } catch (Throwable $e) {
            Storage::disk('public')->delete($path);
            throw $e;
        }

        return response()->json(['message' => 'Dokumentasi ditambahkan.', 'data' => $documentation], 201);
    }

    public function update(Request $request, Activity $activity, Documentation $documentation): JsonResponse
    {
        $data = $request->validate(['caption' => ['present', 'nullable', 'string', 'max:2000']]);
        $documentation = DB::transaction(function () use ($request, $activity, $documentation, $data) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            $documentation = $locked->documentations()->lockForUpdate()->findOrFail($documentation->id);
            $documentation->update($data);
            ActivityLog::record($request->user(), 'documentation.updated', $locked, ['documentation_id' => $documentation->id]);

            return $documentation;
        });

        return response()->json(['message' => 'Keterangan diperbarui.', 'data' => $documentation]);
    }

    public function destroy(Request $request, Activity $activity, Documentation $documentation): JsonResponse
    {
        DB::transaction(function () use ($request, $activity, $documentation) {
            $locked = Activity::lockForUpdate()->findOrFail($activity->id);
            ActivityAccess::manage($request->user(), $locked);
            $documentation = $locked->documentations()->lockForUpdate()->findOrFail($documentation->id);
            ActivityLog::record($request->user(), 'documentation.deleted', $locked, ['documentation_id' => $documentation->id]);
            $documentation->delete();
        });

        return response()->json(['message' => 'Dokumentasi diarsipkan.']);
    }
}
