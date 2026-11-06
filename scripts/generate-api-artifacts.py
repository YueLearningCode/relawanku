"""Generate the importable API contracts from a shared endpoint catalogue."""

import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def schema(kind="string", **kwargs):
    return {"type": kind, **kwargs}


def obj(properties, required=()):
    result = {"type": "object", "properties": properties}
    if required:
        result["required"] = list(required)
    return result


def ref(name):
    return {"$ref": f"#/components/schemas/{name}"}


text = schema()
integer = schema("integer", minimum=1)
date = schema(format="date-time")
role = schema(enum=["relawan", "koordinator", "admin"])
task_status = schema(enum=["pending", "in_progress", "completed"])
user_input = {"name": schema(maxLength=100), "email": schema(format="email"), "password": schema(minLength=8), "password_confirmation": text, "phone": schema(maxLength=25, nullable=True)}
activity_input = {
    "title": schema(maxLength=150), "description": schema(maxLength=10000),
    "category": schema(maxLength=60), "location": schema(maxLength=255),
    "requirements": schema(nullable=True, maxLength=5000), "quota": schema("integer", minimum=1, maximum=100000),
    "starts_at": date, "ends_at": date, "registration_deadline": date,
    "status": schema(enum=["draft", "open", "closed", "completed", "cancelled"]),
    "coordinator_id": schema("integer", minimum=1, description="Wajib saat admin membuat kegiatan; dilarang untuk koordinator karena diambil dari token."),
}
activity_required = ["title", "description", "category", "location", "quota", "starts_at", "ends_at", "registration_deadline"]
task_input = {"title": schema(maxLength=150), "description": schema(nullable=True, maxLength=5000), "assigned_to": schema("integer", nullable=True, minimum=1), "due_at": schema(format="date-time", nullable=True), "status": task_status}
bulk_input = obj({"ids": schema("array", items=integer, minItems=1, maxItems=100, uniqueItems=True)}, ["ids"])

ENDPOINTS = []


def endpoint(method, path, name, group, access="semua", body=None, example=None, response=None, status=200, multipart=False, query=None):
    entry = dict(method=method, path=path, name=name, group=group, access=access, body=body, example=example, response=response, status=status, multipart=multipart, query=query or {})
    ENDPOINTS.append(entry)
    return entry


endpoint("GET", "/health", "Status API", "Umum", "publik", response="Health")
endpoint("POST", "/auth/register", "Daftar relawan", "Autentikasi", "publik", obj(user_input, ["name", "email", "password", "password_confirmation"]), {"name": "Relawan Baru", "email": "relawan.{{$timestamp}}@example.test", "password": "Password123!", "password_confirmation": "Password123!"}, "User", 201)
endpoint("POST", "/auth/login", "Login", "Autentikasi", "publik", obj({"email": schema(format="email"), "password": text, "device_name": schema(maxLength=100)}, ["email", "password", "device_name"]), {"email": "{{coordinator_email}}", "password": "{{demo_password}}", "device_name": "Postman"}, "Login")
endpoint("GET", "/auth/me", "Profil saya", "Autentikasi", response="User")
endpoint("PATCH", "/auth/me", "Perbarui profil", "Autentikasi", body=obj({k: v for k, v in user_input.items() if k not in ["password", "password_confirmation"]} | {"bio": schema(nullable=True, maxLength=2000)}), example={"name": "Relawan Demo", "bio": "Siap berbagi waktu untuk kegiatan sosial."}, response="User")
endpoint("POST", "/auth/logout", "Logout token saat ini", "Autentikasi")
endpoint("GET", "/dashboard", "Ringkasan sesuai peran", "Dashboard", response="Dashboard")
endpoint("GET", "/history", "Riwayat aksi saya", "Dashboard", response="ActivityLog[]")
endpoint("GET", "/my/registrations", "Riwayat keikutsertaan saya", "Pendaftaran", "relawan", response="Registration[]")
endpoint("GET", "/activities", "Daftar dan cari kegiatan", "Kegiatan", response="Activity[]", query={"search": text, "category": text, "status": activity_input["status"], "mine": schema("boolean"), "per_page": schema("integer", minimum=1, maximum=100)})
activity_example = {"title": "Kegiatan Postman {{$timestamp}}", "description": "Kegiatan pengujian API Relawanku.", "category": "Lingkungan", "location": "Makassar", "requirements": "Mengikuti arahan koordinator.", "quota": 30, "starts_at": "{{starts_at}}", "ends_at": "{{ends_at}}", "registration_deadline": "{{registration_deadline}}", "status": "open"}
endpoint("POST", "/activities", "Buat kegiatan", "Kegiatan", "koordinator/admin", obj(activity_input, activity_required), activity_example, "Activity", 201)
endpoint("GET", "/activities/{activity}", "Detail kegiatan", "Kegiatan", response="Activity")
endpoint("PATCH", "/activities/{activity}", "Perbarui kegiatan", "Kegiatan", "koordinator/admin", obj(activity_input), {"title": "Kegiatan Postman Diperbarui", "quota": 35}, "Activity")
endpoint("DELETE", "/activities/{activity}", "Arsipkan kegiatan", "Kegiatan", "koordinator/admin")
endpoint("DELETE", "/activities/bulk", "Arsipkan banyak kegiatan", "Kegiatan", "koordinator/admin", bulk_input, {"ids": ["{{activity_id}}"]})
endpoint("POST", "/activities/{activity}/cover", "Unggah sampul kegiatan", "Kegiatan", "koordinator/admin", obj({"image": schema(format="binary", description="JPEG/PNG/WebP maksimal 5 MB.")}, ["image"]), {"image": ""}, "Activity", multipart=True)
endpoint("GET", "/activities/{activity}/history", "Riwayat pengelolaan kegiatan", "Kegiatan", "koordinator/admin", response="ActivityLog[]")
endpoint("POST", "/activities/{activity}/registrations", "Daftar kegiatan", "Pendaftaran", "relawan", response="Registration", status=201)
endpoint("GET", "/activities/{activity}/registrations", "Daftar peserta kegiatan", "Pendaftaran", "koordinator/admin", response="Registration[]")
endpoint("DELETE", "/activities/{activity}/registration", "Batalkan pendaftaran saya", "Pendaftaran", "relawan")
endpoint("DELETE", "/activities/{activity}/registrations/{registration}", "Batalkan pendaftaran peserta", "Pendaftaran", "koordinator/admin")
endpoint("GET", "/activities/{activity}/tasks", "Daftar tugas", "Tugas", "peserta/koordinator/admin", response="Task[]")
endpoint("POST", "/activities/{activity}/tasks", "Buat tugas", "Tugas", "koordinator/admin", obj(task_input, ["title"]), {"title": "Persiapan perlengkapan", "description": "Siapkan logistik kegiatan.", "assigned_to": "{{volunteer_id}}", "status": "pending"}, "Task", 201)
endpoint("GET", "/activities/{activity}/tasks/{task}", "Detail tugas", "Tugas", "penerima tugas/koordinator/admin", response="Task")
endpoint("PATCH", "/activities/{activity}/tasks/{task}", "Perbarui tugas", "Tugas", "koordinator/admin", obj(task_input), {"description": "Siapkan kantong sampah dan sarung tangan."}, "Task")
endpoint("PATCH", "/activities/{activity}/tasks/{task}/status", "Perbarui status tugas saya", "Tugas", "relawan", obj({"status": task_status}, ["status"]), {"status": "completed"}, "Task")
endpoint("DELETE", "/activities/{activity}/tasks/{task}", "Arsipkan tugas", "Tugas", "koordinator/admin")
endpoint("DELETE", "/activities/{activity}/tasks/bulk", "Arsipkan banyak tugas", "Tugas", "koordinator/admin", bulk_input, {"ids": ["{{task_id}}"]})
endpoint("POST", "/activities/{activity}/attendance-sessions", "Buka sesi absensi", "Absensi", "koordinator/admin", obj({"duration_minutes": schema("integer", minimum=1, maximum=120)}, ["duration_minutes"]), {"duration_minutes": 15}, "OpenedSession", 201)
endpoint("DELETE", "/activities/{activity}/attendance-sessions/{attendanceSession}", "Tutup sesi absensi", "Absensi", "koordinator/admin")
endpoint("POST", "/activities/{activity}/attendances", "Absensi dengan kode", "Absensi", "relawan", obj({"code": schema(pattern="^[A-Fa-f0-9]{8}$")}, ["code"]), {"code": "{{attendance_code}}"}, "Attendance", 201)
endpoint("GET", "/activities/{activity}/attendances", "Daftar kehadiran peserta", "Absensi", "koordinator/admin", response="Registration[]")
endpoint("GET", "/activities/{activity}/documentations", "Daftar dokumentasi", "Dokumentasi", "peserta/koordinator/admin", response="Documentation[]")
endpoint("POST", "/activities/{activity}/documentations", "Unggah dokumentasi", "Dokumentasi", "koordinator/admin", obj({"image": schema(format="binary", description="JPEG/PNG/WebP maksimal 5 MB."), "caption": schema(nullable=True, maxLength=2000)}, ["image"]), {"image": "", "caption": "Dokumentasi kegiatan sosial."}, "Documentation", 201, multipart=True)
endpoint("PATCH", "/activities/{activity}/documentations/{documentation}", "Perbarui keterangan foto", "Dokumentasi", "koordinator/admin", obj({"caption": schema(nullable=True, maxLength=2000)}, ["caption"]), {"caption": "Relawan bekerja bersama."}, "Documentation")
endpoint("DELETE", "/activities/{activity}/documentations/{documentation}", "Arsipkan dokumentasi", "Dokumentasi", "koordinator/admin")
endpoint("GET", "/users", "Daftar pengguna", "Admin", "admin", response="User[]", query={"search": text, "role": role})
endpoint("POST", "/users", "Buat akun pengguna", "Admin", "admin", obj(user_input | {"role": role}, ["name", "email", "password", "password_confirmation", "role"]), {"name": "Koordinator Baru", "email": "koordinator.{{$timestamp}}@example.test", "password": "Password123!", "password_confirmation": "Password123!", "role": "koordinator"}, "User", 201)
endpoint("PATCH", "/users/{user}", "Perbarui peran atau status akun", "Admin", "admin", obj({k: v for k, v in user_input.items() if k not in ["password", "password_confirmation"]} | {"role": role, "is_active": schema("boolean")}), {"is_active": False}, "User")

models = {
    "User": obj({"id": integer, "name": text, "email": schema(format="email"), "role": role, "phone": schema(nullable=True), "bio": schema(nullable=True), "is_active": schema("boolean"), "created_at": date, "updated_at": date}),
    "Activity": obj(activity_input | {"id": integer, "cover_path": schema(nullable=True), "cover_url": schema(nullable=True), "active_registrations_count": schema("integer"), "my_registration": {"allOf": [ref("MyRegistration")], "nullable": True, "description": "Hanya detail kegiatan: pendaftaran pengguna saat ini atau null."}, "created_at": date, "updated_at": date, "deleted_at": schema(format="date-time", nullable=True)}),
    "Registration": obj({"id": integer, "activity_id": integer, "user_id": integer, "status": schema(enum=["registered", "cancelled"]), "registered_at": date, "cancelled_at": schema(format="date-time", nullable=True), "activity": ref("Activity"), "attendance": {"allOf": [ref("Attendance")], "nullable": True}, "user": obj({"id": integer, "name": text, "email": schema(format="email"), "phone": schema(nullable=True)})}),
    "MyRegistration": obj({"id": integer, "activity_id": integer, "user_id": integer, "status": schema(enum=["registered", "cancelled"]), "registered_at": date, "cancelled_at": schema(format="date-time", nullable=True), "attendance": {**obj({"id": integer, "registration_id": integer, "checked_in_at": date}), "nullable": True}}),
    "Task": obj(task_input | {"id": integer, "activity_id": integer, "created_at": date, "updated_at": date}),
    "Attendance": obj({"id": integer, "registration_id": integer, "attendance_session_id": integer, "checked_in_at": date}),
    "AttendanceSession": obj({"id": integer, "activity_id": integer, "created_by": integer, "expires_at": date, "closed_at": schema(format="date-time", nullable=True)}),
    "Documentation": obj({"id": integer, "activity_id": integer, "uploaded_by": integer, "image_path": text, "image_url": text, "caption": schema(nullable=True), "created_at": date, "updated_at": date}),
    "ActivityLog": obj({"id": integer, "actor_id": schema("integer", nullable=True), "activity_id": schema("integer", nullable=True), "event": text, "properties": schema("object", nullable=True), "created_at": date}),
    "Login": obj({"user": ref("User"), "token": text, "token_type": schema(example="Bearer"), "expires_at": date}),
    "OpenedSession": obj({"session": ref("AttendanceSession"), "code": schema(example="A1B2C3D4")}),
    "Dashboard": schema("object", additionalProperties=schema("integer")),
    "Health": obj({"message": text, "version": text}),
    "Error": obj({"message": text, "errors": schema("object", additionalProperties=schema("array", items=text))}),
}

spec = {"openapi": "3.0.3", "info": {"title": "Relawanku REST API", "version": "1.0.0", "description": "API Android Relawanku. Waktu ISO 8601, penyimpanan UTC. Koordinator mengelola kegiatan sendiri; admin lintas kegiatan. Data foto pada disk public memiliki URL publik. Lihat docs/API.md untuk aturan bisnis dan alur Postman."}, "servers": [{"url": "http://127.0.0.1:8000/api", "description": "Server lokal"}], "security": [{"bearerAuth": []}], "paths": {}, "components": {"securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer", "bearerFormat": "Sanctum"}}, "schemas": models}}

parameter_names = {"activity": "activity_id", "task": "task_id", "registration": "registration_id", "documentation": "documentation_id", "attendanceSession": "attendance_session_id", "user": "user_id"}


def response_schema(entry):
    name = entry["response"]
    if not name:
        return obj({"message": text})
    if name == "Health":
        return ref(name)
    if name.endswith("[]"):
        return obj({"data": schema("array", items=ref(name[:-2])), "current_page": integer, "per_page": integer, "total": schema("integer"), "last_page": integer, "next_page_url": schema(nullable=True), "prev_page_url": schema(nullable=True)})
    return obj({"message": text, "data": ref(name)})


for entry in ENDPOINTS:
    operation = {"summary": entry["name"], "description": "Akses: " + entry["access"] + ".", "tags": [entry["group"]], "operationId": entry["method"].lower() + "_" + entry["path"].strip("/").replace("/", "_").replace("{", "").replace("}", ""), "responses": {str(entry["status"]): {"description": "Berhasil", "content": {"application/json": {"schema": response_schema(entry)}}}}}
    if entry["access"] == "publik":
        operation["security"] = []
    parameters = []
    for key in parameter_names:
        if "{" + key + "}" in entry["path"]:
            parameters.append({"name": key, "in": "path", "required": True, "schema": integer})
    query = dict(entry["query"])
    if entry["response"] and entry["response"].endswith("[]"):
        query["page"] = integer
    parameters += [{"name": key, "in": "query", "schema": value} for key, value in query.items()]
    if parameters:
        operation["parameters"] = parameters
    if entry["body"]:
        media = "multipart/form-data" if entry["multipart"] else "application/json"
        operation["requestBody"] = {"required": True, "content": {media: {"schema": entry["body"]}}}
    for code, description in {401: "Token tidak ada, kedaluwarsa, atau dicabut", 403: "Tidak memiliki akses", 404: "Data tidak ditemukan", 409: "Konflik aturan bisnis", 422: "Validasi gagal", 429: "Terlalu banyak permintaan"}.items():
        operation["responses"][str(code)] = {"description": description, "content": {"application/json": {"schema": ref("Error")}}}
    spec["paths"].setdefault(entry["path"], {})[entry["method"].lower()] = operation
    if entry["method"] == "PATCH" and entry["path"] in ["/activities/{activity}", "/activities/{activity}/tasks/{task}", "/activities/{activity}/documentations/{documentation}", "/users/{user}"]:
        put = copy.deepcopy(operation)
        put["operationId"] = put["operationId"].replace("patch_", "put_", 1)
        spec["paths"][entry["path"]]["put"] = put


def events(kind, lines):
    return {"listen": kind, "script": {"type": "text/javascript", "exec": lines}}


def request(entry, name=None, token=None, capture=None, before=None, body=None):
    path = entry["path"]
    for key, variable in parameter_names.items():
        path = path.replace("{" + key + "}", "{{" + variable + "}}")
    req = {"method": entry["method"], "header": [{"key": "Accept", "value": "application/json"}], "url": "{{base_url}}" + path, "description": "Akses: " + entry["access"] + ". " + ("Pilih berkas image di Body > form-data, lalu jalankan manual." if entry["multipart"] else "")}
    if entry["access"] == "publik":
        req["auth"] = {"type": "noauth"}
    else:
        token = token or ("admin_token" if entry["access"] == "admin" else "coordinator_token" if entry["access"] == "koordinator/admin" else "volunteer_token")
        req["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "{{" + token + "}}", "type": "string"}]}
    if entry["body"]:
        payload = entry["example"] if body is None else body
        if entry["multipart"]:
            req["body"] = {"mode": "formdata", "formdata": [{"key": key, "type": "file" if key == "image" else "text", **({"src": ""} if key == "image" else {"value": str(value)})} for key, value in payload.items()]}
        else:
            req["header"].append({"key": "Content-Type", "value": "application/json"})
            raw = json.dumps(payload, ensure_ascii=False, indent=2)
            for variable in ["activity_id", "task_id", "volunteer_id", "coordinator_id", "user_id"]:
                raw = raw.replace('"{{' + variable + '}}"', '{{' + variable + '}}')
            req["body"] = {"mode": "raw", "raw": raw, "options": {"raw": {"language": "json"}}}
    lines = [f'pm.test("HTTP {entry["status"]}", () => pm.response.to.have.status({entry["status"]}));']
    if capture:
        lines += ['if (pm.response.code >= 200 && pm.response.code < 300) {', '  const data = pm.response.json().data;', *[f'  pm.environment.set("{key}", data.{value});' for key, value in capture.items()], '}']
    item = {"name": name or entry["name"], "request": req, "response": [], "event": [events("test", lines)]}
    if before:
        item["event"].append(events("prerequest", before))
    return item


def find(method, path):
    return next(entry for entry in ENDPOINTS if entry["method"] == method and entry["path"] == path)


future = [
    'const start = new Date(Date.now() + 48 * 3600000);',
    'pm.environment.set("starts_at", start.toISOString());',
    'pm.environment.set("ends_at", new Date(start.getTime() + 3 * 3600000).toISOString());',
    'pm.environment.set("registration_deadline", new Date(start.getTime() - 24 * 3600000).toISOString());',
]
ongoing = [
    'pm.environment.set("ongoing_start", new Date(Date.now() - 60000).toISOString());',
    'pm.environment.set("ongoing_end", new Date(Date.now() + 3600000).toISOString());',
    'pm.environment.set("ongoing_deadline", new Date(Date.now() - 120000).toISOString());',
]
workflow = [request(find("GET", "/health"))]
for key, label in [("admin", "Admin"), ("coordinator", "Koordinator"), ("volunteer", "Relawan")]:
    workflow.append(request(find("POST", "/auth/login"), name="Login " + label, body={"email": "{{" + key + "_email}}", "password": "{{demo_password}}", "device_name": "Postman"}, capture={key + "_token": "token", key + "_id": "user.id"}))
workflow += [
    request(find("POST", "/activities"), before=future, capture={"activity_id": "id"}),
    request(find("GET", "/activities")), request(find("GET", "/activities/{activity}")),
    request(find("POST", "/activities/{activity}/registrations"), capture={"registration_id": "id"}),
    request(find("GET", "/activities/{activity}/registrations")), request(find("GET", "/my/registrations")),
    request(find("POST", "/activities/{activity}/tasks"), capture={"task_id": "id"}),
    request(find("GET", "/activities/{activity}/tasks")),
    request(find("PATCH", "/activities/{activity}/tasks/{task}/status")),
    request(find("PATCH", "/activities/{activity}"), name="Simulasikan kegiatan sedang berlangsung", before=ongoing, body={"starts_at": "{{ongoing_start}}", "ends_at": "{{ongoing_end}}", "registration_deadline": "{{ongoing_deadline}}", "status": "closed"}),
    request(find("POST", "/activities/{activity}/attendance-sessions"), capture={"attendance_session_id": "session.id", "attendance_code": "code"}),
    request(find("POST", "/activities/{activity}/attendances")),
    request(find("GET", "/activities/{activity}/attendances")),
    request(find("GET", "/dashboard")), request(find("GET", "/history")),
    request(find("GET", "/activities/{activity}/history")),
    request(find("DELETE", "/activities/{activity}/attendance-sessions/{attendanceSession}")),
    request(find("DELETE", "/activities/{activity}/tasks/{task}")),
    request(find("DELETE", "/activities/{activity}")),
]

reference = []
for group in dict.fromkeys(e["group"] for e in ENDPOINTS):
    entries = []
    for entry in ENDPOINTS:
        if entry["group"] != group:
            continue
        capture = None
        if entry["method"] == "POST" and entry["path"] in ["/activities", "/activities/{activity}/tasks", "/activities/{activity}/registrations", "/activities/{activity}/documentations", "/users"]:
            variable = {"/activities": "activity_id", "/activities/{activity}/tasks": "task_id", "/activities/{activity}/registrations": "registration_id", "/activities/{activity}/documentations": "documentation_id", "/users": "user_id"}[entry["path"]]
            capture = {variable: "id"}
        elif entry["path"] == "/auth/login":
            capture = {"coordinator_token": "token", "coordinator_id": "user.id"}
        elif entry["method"] == "POST" and entry["path"].endswith("/attendance-sessions"):
            capture = {"attendance_session_id": "session.id", "attendance_code": "code"}
        entries.append(request(entry, capture=capture, before=future if entry["method"] == "POST" and entry["path"] == "/activities" else None))
    reference.append({"name": group, "item": entries})

collection = {"info": {"name": "Relawanku API", "description": "Import environment lokal dan jalankan DemoSeeder. Jalankan folder 01 Alur utama sebagai smoke test. Folder 02 adalah referensi endpoint untuk dijalankan satu per satu; hapus massal dan pembatalan memiliki prasyarat berbeda. Jangan menjalankan seluruh koleksi sekaligus. Token tiap peran dan ID hasil create tersimpan otomatis. Unggahan foto membutuhkan pemilihan file manual. Lihat docs/API.md.", "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"}, "item": [{"name": "01 Alur utama - jalankan folder ini", "description": "Login tiga peran, CRUD kegiatan dan tugas, pendaftaran, sesi absensi, kehadiran, riwayat, dan arsip data uji. Dapat dijalankan ulang; membuat kegiatan baru setiap run.", "item": workflow}, {"name": "02 Referensi endpoint - jalankan satu per satu", "item": reference}]}
values = {
    "base_url": "http://127.0.0.1:8000/api", "admin_email": "admin@relawanku.test", "coordinator_email": "koordinator@relawanku.test", "volunteer_email": "relawan@relawanku.test", "demo_password": "RelawanDemo123!",
    **{key: "" for key in ["admin_token", "coordinator_token", "volunteer_token", "admin_id", "coordinator_id", "volunteer_id", "activity_id", "task_id", "registration_id", "documentation_id", "attendance_session_id", "attendance_code", "user_id", "starts_at", "ends_at", "registration_deadline", "ongoing_start", "ongoing_end", "ongoing_deadline"]},
}
environment = {"name": "Relawanku - Local", "values": [{"key": key, "value": value, "enabled": True, "type": "secret" if "token" in key or "password" in key or key == "attendance_code" else "default"} for key, value in values.items()], "_postman_variable_scope": "environment"}

for relative, data in [("docs/openapi.json", spec), ("postman/Relawanku.postman_collection.json", collection), ("postman/Relawanku.local.postman_environment.json", environment)]:
    destination = ROOT / relative
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Generated OpenAPI ({len(ENDPOINTS)} operations + PUT aliases), Postman collection and environment.")
