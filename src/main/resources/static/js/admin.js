(function () {
    "use strict";

    const app = window.HealthcareApp;
    const content = document.getElementById("admin-content");
    if (!app || !content) {
        return;
    }

    const today = localDate();
    const routes = new Set(["dashboard", "predictions", "allocation", "departments", "doctors", "staff", "reports", "audit"]);
    const charts = [];
    let attendanceDate = today;
    let activeDepartments = [];
    let currentAuditPage = 0;
    let auditFilters = { user: "", action: "", date: "" };

    function localDate() {
        const now = new Date();
        return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    }

    function escapeHtml(value) {
        return String(value == null ? "" : value).replace(/[&<>"']/g, (character) => ({
            "&": "&amp;",
            "<": "&lt;",
            ">": "&gt;",
            "\"": "&quot;",
            "'": "&#39;"
        })[character]);
    }

    function heading(title, subtitle, action) {
        return `<div class="page-heading"><div><p class="eyebrow">Administrator Portal</p><h1>${escapeHtml(title)}</h1><p class="page-subtitle">${escapeHtml(subtitle)}</p></div>${action || ""}</div>`;
    }

    function empty(message, title) {
        return `<div class="empty-state"><div><strong>${escapeHtml(title || "Nothing here yet")}</strong>${escapeHtml(message)}</div></div>`;
    }

    function loading(label) {
        content.innerHTML = `${heading("Loading…", label)}<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>${escapeHtml(label)}</span></div>`;
    }

    function fail(error) {
        app.showToast(error.message || "The request could not be completed.", "error");
    }

    function json(method, value) {
        return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(value) };
    }

    function badge(status) {
        const key = String(status || "").toUpperCase();
        const style = key === "OK" || key === "APPROVED" || key === "ENABLED" ? " badge-ok"
            : key === "WARNING" || key === "SUGGESTED" ? " badge-warning"
                : key === "SHORTAGE" || key === "REJECTED" || key === "DISABLED" ? " badge-shortage" : "";
        return `<span class="badge${style}">${escapeHtml(key.replaceAll("_", " "))}</span>`;
    }

    function percent(value) {
        const number = Number(value || 0) * 100;
        return `${number.toFixed(0)}%`;
    }

    function statusClass(status) {
        return String(status).toLowerCase();
    }

    function nav(view) {
        document.querySelectorAll("[data-admin-nav]").forEach((link) => {
            if (link.dataset.adminNav === view) {
                link.setAttribute("aria-current", "page");
            } else {
                link.removeAttribute("aria-current");
            }
        });
    }

    function destroyCharts() {
        while (charts.length) {
            charts.pop().destroy();
        }
    }

    function createChart(canvas, config) {
        if (!window.Chart || !canvas) {
            return false;
        }
        charts.push(new window.Chart(canvas, config));
        return true;
    }

    function chartFallback() {
        return `<p class="text-muted">Charts need Chart.js to load. Check network access and refresh this page.</p>`;
    }

    async function render(view) {
        const page = routes.has(view) ? view : "dashboard";
        nav(page);
        destroyCharts();
        content.focus({ preventScroll: true });
        const renderers = {
            dashboard: renderDashboard,
            predictions: renderPredictions,
            allocation: renderAllocation,
            departments: renderDepartments,
            doctors: renderDoctors,
            staff: renderStaff,
            reports: renderReports,
            audit: renderAudit
        };
        await renderers[page]();
    }

    async function renderDashboard() {
        loading("Loading hospital summary and forecasts…");
        try {
            const [summary, predictions, allocation, occupancy] = await Promise.all([
                app.json("/api/admin/summary"),
                app.json("/api/admin/predictions"),
                app.json("/api/admin/allocation"),
                app.json("/api/staff/beds/occupancy")
            ]);
            activeDepartments = allocation.departments;
            const alerts = allocation.departments.filter((department) => department.status === "WARNING" || department.status === "SHORTAGE");
            content.innerHTML = `
                ${heading("Dashboard", "Today's operational overview and upcoming department demand.")}
                <section class="content-grid" aria-label="Hospital summary">
                    <article class="card grid-span-3"><div class="stat-label">Patients today</div><div class="stat-value">${summary.patientsToday}</div></article>
                    <article class="card grid-span-3"><div class="stat-label">Beds in use</div><div class="stat-value">${summary.bedsInUse}</div></article>
                    <article class="card grid-span-3"><div class="stat-label">Doctors on duty</div><div class="stat-value">${summary.doctorsOnDuty}</div></article>
                    <article class="card grid-span-3"><div class="stat-label">Departments in shortage</div><div class="stat-value">${summary.departmentsInShortage}</div></article>
                    <article class="card grid-span-8">
                        <div class="card-header"><h2>Predicted load · next 7 days</h2><a href="#predictions">View predictions</a></div>
                        <div class="chart-wrap"><canvas id="predicted-load-chart" role="img" aria-label="Predicted daily load by department for the next seven days"></canvas></div>
                        <div id="predicted-load-fallback"></div>
                    </article>
                    <article class="card grid-span-4">
                        <div class="card-header"><h2>Department alerts</h2><a href="#allocation">Allocation</a></div>
                        <div class="alert-list">${alerts.length ? alerts.map((department) => `<div class="alert-item">${badge(department.status)}<div><strong>${escapeHtml(department.department)}</strong><p>Beds ${percent(department.bedsUtilization)} · doctors ${percent(department.doctorsUtilization)}</p></div></div>`).join("") : empty("All departments are within capacity thresholds.", "No active alerts")}</div>
                    </article>
                    <article class="card grid-span-12">
                        <div class="card-header"><h2>Bed occupancy by department</h2></div>
                        <div class="chart-wrap"><canvas id="bed-occupancy-chart" role="img" aria-label="Occupied, available and maintenance beds by department"></canvas></div>
                        <div id="bed-occupancy-fallback"></div>
                    </article>
                </section>`;
            const labels = predictions.map((item) => item.department);
            const dates = predictions[0] ? predictions[0].forecast.map((day) => day.date) : [];
            const datasets = predictions.map((item, index) => ({
                label: item.department,
                data: item.forecast.map((day) => day.predictedPatients),
                borderColor: chartColor(index),
                backgroundColor: chartColor(index),
                tension: 0.3,
                fill: false
            }));
            if (!createChart(document.getElementById("predicted-load-chart"), {
                type: "line",
                data: { labels: dates, datasets },
                options: chartOptions("Predicted patients", true)
            })) {
                document.getElementById("predicted-load-fallback").innerHTML = chartFallback();
            }
            if (!createChart(document.getElementById("bed-occupancy-chart"), {
                type: "bar",
                data: {
                    labels: occupancy.map((row) => row.departmentName),
                    datasets: [
                        { label: "Occupied", data: occupancy.map((row) => row.occupied), backgroundColor: "#D9735B" },
                        { label: "Available", data: occupancy.map((row) => row.available), backgroundColor: "#5FC4B8" },
                        { label: "Maintenance", data: occupancy.map((row) => row.maintenance), backgroundColor: "#8A8176" }
                    ]
                },
                options: chartOptions("Beds", false)
            })) {
                document.getElementById("bed-occupancy-fallback").innerHTML = chartFallback();
            }
        } catch (error) {
            content.innerHTML = `${heading("Dashboard", "Today's operational overview and upcoming department demand.")}${empty(error.message, "Dashboard could not load")}`;
            fail(error);
        }
    }

    function chartColor(index) {
        const colors = ["#C8A27A", "#2B817B", "#5A4632", "#D9735B", "#74864A", "#8B6AA8", "#D29A30"];
        return colors[index % colors.length];
    }

    function chartOptions(axisTitle, line) {
        return {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { intersect: false, mode: "index" },
            plugins: { legend: { position: "bottom" } },
            scales: {
                y: { beginAtZero: true, title: { display: true, text: axisTitle }, ticks: { precision: 0 } },
                x: { grid: { display: !line } }
            }
        };
    }

    async function renderPredictions() {
        loading("Loading department predictions…");
        try {
            const [predictions, departments] = await Promise.all([
                app.json("/api/admin/predictions"),
                app.json("/api/admin/departments")
            ]);
            const chosenId = predictions[0] ? predictions[0].departmentId : "";
            content.innerHTML = `
                ${heading("Predictions", "Compare recent patient history with each department's seven-day forecast.")}
                <article class="card">
                    <div class="filter-row">
                        <div class="field"><label for="prediction-department">Department</label><select class="form-control" id="prediction-department">${departments.map((item) => `<option value="${item.id}"${String(item.id) === String(chosenId) ? " selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}</select></div>
                    </div>
                    <div id="prediction-content"></div>
                </article>`;
            document.getElementById("prediction-department").addEventListener("change", (event) => {
                const department = predictions.find((item) => String(item.departmentId) === event.target.value);
                if (department) {
                    drawDepartmentPrediction(department);
                }
            });
            if (!predictions.length) {
                document.getElementById("prediction-content").innerHTML = empty("No department prediction data is available.", "No predictions");
            } else {
                drawDepartmentPrediction(predictions.find((item) => String(item.departmentId) === String(chosenId)) || predictions[0]);
            }
        } catch (error) {
            content.innerHTML = `${heading("Predictions", "Compare recent patient history with each department's seven-day forecast.")}${empty(error.message, "Predictions could not load")}`;
            fail(error);
        }
    }

    function drawDepartmentPrediction(department) {
        destroyCharts();
        const host = document.getElementById("prediction-content");
        if (!host) {
            return;
        }
        const rows = [...department.history.map((item) => ({ ...item, kind: "History" })),
            ...department.forecast.map((item) => ({ ...item, kind: "Forecast" }))];
        host.innerHTML = `
            <div class="chart-wrap"><canvas id="department-prediction-chart" role="img" aria-label="Patient history and forecast for ${escapeHtml(department.department)}"></canvas></div>
            <div id="department-prediction-fallback"></div>
            <h2 style="margin:22px 0 12px">7-day forecast</h2>
            ${department.forecast.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Date</th><th>Predicted patients</th></tr></thead><tbody>${department.forecast.map((item) => `<tr><td>${escapeHtml(item.date)}</td><td>${item.predictedPatients}</td></tr>`).join("")}</tbody></table></div>` : empty("No forecast days are available.", "No forecast")}
            <p class="text-muted" style="margin-top:14px">History points: ${department.history.length} · Forecast points: ${department.forecast.length}</p>`;
        const labels = rows.map((item) => item.date);
        const valuesHistory = rows.map((item) => item.kind === "History" ? item.predictedPatients : null);
        const valuesForecast = rows.map((item) => item.kind === "Forecast" ? item.predictedPatients : null);
        if (!createChart(document.getElementById("department-prediction-chart"), {
            type: "line",
            data: {
                labels,
                datasets: [
                    { label: "History", data: valuesHistory, borderColor: "#C8A27A", backgroundColor: "rgba(200,162,122,.14)", tension: 0.25, spanGaps: true },
                    { label: "Forecast", data: valuesForecast, borderColor: "#2B817B", backgroundColor: "rgba(142,216,208,.18)", tension: 0.25, spanGaps: true }
                ]
            },
            options: chartOptions("Patients", true)
        })) {
            document.getElementById("department-prediction-fallback").innerHTML = chartFallback();
        }
    }

    async function renderAllocation() {
        loading("Loading department needs and transfer suggestions…");
        try {
            const report = await app.json("/api/admin/allocation");
            const transfers = await app.json("/api/admin/transfers");
            activeDepartments = report.departments;
            const departments = report.departments || [];
            const suggestions = transfers.filter((item) => item.status === "SUGGESTED");
            const history = transfers.filter((item) => item.status !== "SUGGESTED");
            content.innerHTML = `
                ${heading("Allocation & transfers", "Review resource utilization and decide on suggested transfers.")}
                <section class="content-grid" aria-label="Department allocation">
                    ${departments.length ? departments.map((department) => allocationCard(department)).join("") : `<div class="grid-span-12">${empty("No departments are configured.", "No allocation data")}</div>`}
                    <article class="card grid-span-12">
                        <div class="card-header"><h2>Suggested transfers</h2><span class="badge badge-warning">${suggestions.length} pending</span></div>
                        <div class="transfer-list">${suggestions.length ? suggestions.map(transferCard).join("") : empty("There are no pending transfer suggestions.", "Nothing to approve")}</div>
                    </article>
                    <article class="card grid-span-12">
                        <div class="card-header"><h2>Transfer history</h2></div>
                        ${history.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>From</th><th>To</th><th>Resource</th><th>Quantity</th><th>Decision</th><th>Decided by</th><th>Reason</th></tr></thead><tbody>${history.map((item) => `<tr><td>${escapeHtml(item.fromDepartment)}</td><td>${escapeHtml(item.toDepartment)}</td><td>${escapeHtml(item.resourceType)}</td><td>${item.quantity}</td><td>${badge(item.status)}</td><td>${escapeHtml(item.decidedBy || "—")}</td><td>${escapeHtml(item.reason)}</td></tr>`).join("")}</tbody></table></div>` : empty("Approved and rejected decisions will appear here.", "No transfer history")}
                    </article>
                </section>`;
            content.querySelectorAll("[data-transfer-action]").forEach((button) => {
                button.addEventListener("click", () => decideTransfer(button));
            });
        } catch (error) {
            content.innerHTML = `${heading("Allocation & transfers", "Review resource utilization and decide on suggested transfers.")}${empty(error.message, "Allocation could not load")}`;
            fail(error);
        }
    }

    function allocationCard(item) {
        const beds = item.bedsUtilization;
        const doctors = item.doctorsUtilization;
        const status = statusClass(item.status);
        return `<article class="card grid-span-4">
            <div class="card-header"><h2>${escapeHtml(item.department)}</h2>${badge(item.status)}</div>
            <p>Forecast: <strong>${item.predictedPatients}</strong> patients</p>
            <div style="margin-top:16px">
                <div class="utilization-label"><span>Beds · need ${Number(item.bedsNeeded).toFixed(1)} / available ${item.bedsAvailable}</span><strong>${percent(beds)}</strong></div>
                <div class="utilization-track" role="progressbar" aria-label="Bed utilization" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.min(100, Math.round(beds * 100))}"><div class="utilization-fill ${status}" style="width:${Math.min(100, Math.max(2, beds * 100))}%"></div></div>
            </div>
            <div style="margin-top:16px">
                <div class="utilization-label"><span>Doctors · need ${item.doctorsNeeded} / on duty ${item.doctorsAvailable}</span><strong>${percent(doctors)}</strong></div>
                <div class="utilization-track" role="progressbar" aria-label="Doctor utilization" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.min(100, Math.round(doctors * 100))}"><div class="utilization-fill ${status}" style="width:${Math.min(100, Math.max(2, doctors * 100))}%"></div></div>
            </div>
        </article>`;
    }

    function transferCard(item) {
        return `<div class="transfer-item">
            <div><div class="quick-actions">${badge(item.resourceType)}<strong>${item.quantity} ${escapeHtml(item.resourceType.toLowerCase())}${item.quantity === 1 ? "" : "s"}</strong></div><p><strong>${escapeHtml(item.fromDepartment)}</strong> → <strong>${escapeHtml(item.toDepartment)}</strong></p><p>${escapeHtml(item.reason)}</p></div>
            <div class="transfer-actions"><button class="btn btn-aqua" type="button" data-transfer-action="approve" data-transfer-id="${item.id}">Approve</button><button class="btn btn-secondary" type="button" data-transfer-action="reject" data-transfer-id="${item.id}">Reject</button></div>
        </div>`;
    }

    async function decideTransfer(button) {
        const action = button.dataset.transferAction;
        if (!(await app.confirmDialog(`${action === "approve" ? "Approve" : "Reject"} this resource transfer?`, `${action === "approve" ? "Approve" : "Reject"} transfer`))) {
            return;
        }
        button.disabled = true;
        try {
            await app.json(`/api/admin/transfers/${button.dataset.transferId}/${action}`, { method: "PUT" });
            app.showToast(`Transfer ${action === "approve" ? "approved" : "rejected"}.`);
            await renderAllocation();
        } catch (error) {
            fail(error);
            button.disabled = false;
        }
    }

    async function loadDepartments() {
        return app.json("/api/admin/departments");
    }

    async function renderDepartments() {
        loading("Loading departments and capacity…");
        try {
            const [departments, occupancy] = await Promise.all([loadDepartments(), app.json("/api/staff/beds/occupancy")]);
            activeDepartments = departments;
            content.innerHTML = `
                ${heading("Departments & capacity", "Manage department settings and physical bed capacity.", `<button class="btn btn-primary" id="add-department" type="button">Add department</button>`)}
                <article class="card">
                    ${departments.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Department</th><th>Bed capacity</th><th>Average stay</th><th>Patients / doctor</th><th>Occupied</th><th>Available</th><th>Actions</th></tr></thead><tbody>${departments.map((department) => {
                        const live = occupancy.find((item) => item.departmentId === department.id) || {};
                        return `<tr><td><strong>${escapeHtml(department.name)}</strong></td><td>${department.bedCapacity}</td><td>${department.averageStay}</td><td>${department.patientsPerDoctor}</td><td>${live.occupied || 0}</td><td>${live.available || 0}</td><td><div class="table-actions"><button class="btn btn-secondary" type="button" data-edit-department="${department.id}">Edit</button><button class="btn btn-danger" type="button" data-delete-department="${department.id}" data-department-name="${escapeHtml(department.name)}">Delete</button></div></td></tr>`;
                    }).join("")}</tbody></table></div>` : empty("Create the first department to begin configuring capacity.", "No departments")}
                </article>`;
            document.getElementById("add-department").addEventListener("click", () => openDepartmentModal());
            content.querySelectorAll("[data-edit-department]").forEach((button) => button.addEventListener("click", () => {
                const department = departments.find((item) => String(item.id) === button.dataset.editDepartment);
                openDepartmentModal(department);
            }));
            content.querySelectorAll("[data-delete-department]").forEach((button) => button.addEventListener("click", () => deleteDepartment(button)));
        } catch (error) {
            content.innerHTML = `${heading("Departments & capacity", "Manage department settings and physical bed capacity.")}${empty(error.message, "Departments could not load")}`;
            fail(error);
        }
    }

    function openDepartmentModal(department) {
        const editing = Boolean(department);
        const form = document.createElement("form");
        form.innerHTML = `
            <div class="form-layout">
                <div class="field"><label for="department-name">Name</label><input class="form-control" id="department-name" name="name" maxlength="100" required value="${escapeHtml(department ? department.name : "")}"></div>
                <div class="field"><label for="department-capacity">Bed capacity</label><input class="form-control" id="department-capacity" name="bedCapacity" type="number" min="0" required value="${department ? department.bedCapacity : 0}"></div>
                <div class="field"><label for="department-stay">Average stay (days)</label><input class="form-control" id="department-stay" name="averageStay" type="number" min="0" step="0.1" required value="${department ? department.averageStay : 1}"></div>
                <div class="field"><label for="department-ratio">Patients per doctor</label><input class="form-control" id="department-ratio" name="patientsPerDoctor" type="number" min="1" required value="${department ? department.patientsPerDoctor : 10}"></div>
            </div>
            <p class="form-error" id="department-error" role="alert"></p>
            <div class="modal-footer"><button class="btn btn-primary" type="submit">${editing ? "Save changes" : "Create department"}</button></div>`;
        const dialog = app.openModal(editing ? "Edit department" : "Add department", form);
        dialog.classList.add("modal-wide");
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const values = Object.fromEntries(new FormData(form).entries());
            values.name = values.name.trim();
            values.bedCapacity = Number(values.bedCapacity);
            values.averageStay = Number(values.averageStay);
            values.patientsPerDoctor = Number(values.patientsPerDoctor);
            if (!values.name || !Number.isInteger(values.bedCapacity) || values.bedCapacity < 0
                || !Number.isFinite(values.averageStay) || values.averageStay < 0
                || !Number.isInteger(values.patientsPerDoctor) || values.patientsPerDoctor < 1) {
                document.getElementById("department-error").textContent = "Enter a name, non-negative capacity and stay, and a positive patients-per-doctor value.";
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await app.json(editing ? `/api/admin/departments/${department.id}` : "/api/admin/departments",
                    json(editing ? "PUT" : "POST", values));
                dialog.close();
                app.showToast(editing ? "Department updated." : "Department created.");
                await renderDepartments();
            } catch (error) {
                document.getElementById("department-error").textContent = error.message;
                button.disabled = false;
            }
        });
    }

    async function deleteDepartment(button) {
        if (!(await app.confirmDialog(`Delete ${button.dataset.departmentName}? This is only allowed when there are no dependent records.`, "Delete department"))) {
            return;
        }
        button.disabled = true;
        try {
            await app.request(`/api/admin/departments/${button.dataset.deleteDepartment}`, { method: "DELETE" }).then(async (response) => {
                if (!response.ok) {
                    const body = await response.json().catch(() => ({}));
                    throw new Error(body.message || "Unable to delete department.");
                }
            });
            app.showToast("Department deleted.");
            await renderDepartments();
        } catch (error) {
            fail(error);
            button.disabled = false;
        }
    }

    async function renderDoctors() {
        loading("Loading doctors and schedules…");
        try {
            const [doctors, departments, attendance] = await Promise.all([
                app.json("/api/admin/doctors"),
                loadDepartments(),
                app.json(`/api/admin/doctor-attendance?date=${encodeURIComponent(attendanceDate)}`)
            ]);
            activeDepartments = departments;
            content.innerHTML = `
                ${heading("Doctors", "Manage department assignments, working hours, slot length and duty status.", `<button class="btn btn-primary" id="add-doctor" type="button">Add doctor</button>`)}
                <article class="card">
                    ${doctors.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Doctor</th><th>Department</th><th>Hours</th><th>Slot</th><th>On duty</th><th>Actions</th></tr></thead><tbody>${doctors.map((doctor) => `<tr><td><strong>${escapeHtml(doctor.name)}</strong></td><td>${escapeHtml(doctor.departmentName)}</td><td>${escapeHtml(doctor.workStart.slice(0, 5))}–${escapeHtml(doctor.workEnd.slice(0, 5))}</td><td>${doctor.slotMinutes} min</td><td>${badge(doctor.onDuty ? "ENABLED" : "DISABLED")}</td><td><div class="table-actions"><button class="btn btn-secondary" type="button" data-edit-doctor="${doctor.id}">Edit</button><button class="btn btn-danger" type="button" data-delete-doctor="${doctor.id}" data-doctor-name="${escapeHtml(doctor.name)}">Delete</button></div></td></tr>`).join("")}</tbody></table></div>` : empty("Add a doctor to configure working hours and appointments.", "No doctors")}
                </article>
                <article class="card">
                    <div class="card-header"><h2>Daily doctor attendance</h2></div>
                    <div class="filter-row">
                        <div class="field"><label for="attendance-date">Date</label><input class="form-control" id="attendance-date" type="date" min="${today}" value="${attendanceDate}"></div>
                        <p class="text-muted">Doctors marked absent will not appear in staff doctor lists for this date.</p>
                    </div>
                    ${attendance.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Doctor</th><th>Department</th><th>Daily attendance</th><th>Action</th></tr></thead><tbody>${attendance.map((item) => `<tr><td><strong>${escapeHtml(item.doctorName)}</strong></td><td>${escapeHtml(item.departmentName)}</td><td><select class="form-control" id="attendance-${item.doctorId}"${item.onDuty ? "" : " disabled"}>${item.onDuty ? `<option value="true"${item.present ? " selected" : ""}>Present</option><option value="false"${!item.present ? " selected" : ""}>Absent</option>` : `<option value="off" selected>Off duty</option>`}</select>${item.onDuty ? "" : `<small class="text-muted">Doctor is off duty in their profile.</small>`}</td><td><button class="btn btn-secondary" type="button" data-save-attendance="${item.doctorId}"${item.onDuty ? "" : " disabled"}>Save</button></td></tr>`).join("")}</tbody></table></div>` : empty("Add doctors to record daily attendance.", "No doctors") }
                </article>`;
            document.getElementById("add-doctor").addEventListener("click", () => openDoctorModal());
            document.getElementById("attendance-date").addEventListener("change", async (event) => {
                attendanceDate = event.target.value || today;
                await renderDoctors();
            });
            content.querySelectorAll("[data-save-attendance]").forEach((button) => {
                button.addEventListener("click", () => saveDoctorAttendance(button));
            });
            content.querySelectorAll("[data-edit-doctor]").forEach((button) => button.addEventListener("click", () => {
                const doctor = doctors.find((item) => String(item.id) === button.dataset.editDoctor);
                openDoctorModal(doctor);
            }));
            content.querySelectorAll("[data-delete-doctor]").forEach((button) => button.addEventListener("click", () => deleteDoctor(button)));
        } catch (error) {
            content.innerHTML = `${heading("Doctors", "Manage department assignments, working schedules, and daily attendance.")}${empty(error.message, "Doctors could not load")}`;
            fail(error);
        }
    }

    async function saveDoctorAttendance(button) {
        const doctorId = button.dataset.saveAttendance;
        const present = document.getElementById(`attendance-${doctorId}`).value === "true";
        button.disabled = true;
        try {
            await app.json(`/api/admin/doctors/${doctorId}/attendance`, json("PUT", {
                date: attendanceDate,
                present
            }));
            app.showToast("Doctor attendance saved.");
            await renderDoctors();
        } catch (error) {
            fail(error);
            button.disabled = false;
        }
    }

    function openDoctorModal(doctor) {
        const editing = Boolean(doctor);
        const form = document.createElement("form");
        form.innerHTML = `
            <div class="form-layout">
                <div class="field"><label for="doctor-name">Doctor name</label><input class="form-control" id="doctor-name" name="name" maxlength="120" required value="${escapeHtml(doctor ? doctor.name : "")}"></div>
                <div class="field"><label for="doctor-department">Department</label><select class="form-control" id="doctor-department" name="departmentId" required><option value="">Choose department</option>${activeDepartments.map((item) => `<option value="${item.id}"${doctor && item.id === doctor.departmentId ? " selected" : ""}>${escapeHtml(item.name)}</option>`).join("")}</select></div>
                <div class="field"><label for="doctor-start">Work starts</label><input class="form-control" id="doctor-start" name="workStart" type="time" required value="${doctor ? doctor.workStart.slice(0, 5) : "09:00"}"></div>
                <div class="field"><label for="doctor-end">Work ends</label><input class="form-control" id="doctor-end" name="workEnd" type="time" required value="${doctor ? doctor.workEnd.slice(0, 5) : "17:00"}"></div>
                <div class="field"><label for="doctor-slot">Slot length (minutes)</label><input class="form-control" id="doctor-slot" name="slotMinutes" type="number" min="5" max="60" required value="${doctor ? doctor.slotMinutes : 20}"></div>
                <label class="switch-control" for="doctor-duty"><input id="doctor-duty" name="onDuty" type="checkbox"${!doctor || doctor.onDuty ? " checked" : ""}><span>On duty</span></label>
            </div>
            <p class="form-error" id="doctor-error" role="alert"></p>
            <div class="modal-footer"><button class="btn btn-primary" type="submit">${editing ? "Save changes" : "Create doctor"}</button></div>`;
        const dialog = app.openModal(editing ? "Edit doctor" : "Add doctor", form);
        dialog.classList.add("modal-wide");
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const values = Object.fromEntries(new FormData(form).entries());
            values.name = values.name.trim();
            values.departmentId = Number(values.departmentId);
            values.slotMinutes = Number(values.slotMinutes);
            values.onDuty = document.getElementById("doctor-duty").checked;
            if (!values.name || !values.departmentId || !values.workStart || !values.workEnd
                || values.workStart >= values.workEnd || !Number.isInteger(values.slotMinutes)
                || values.slotMinutes < 5 || values.slotMinutes > 60) {
                document.getElementById("doctor-error").textContent = "Provide all fields, work start before end, and a slot length from 5 to 60 minutes.";
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await app.json(editing ? `/api/admin/doctors/${doctor.id}` : "/api/admin/doctors",
                    json(editing ? "PUT" : "POST", values));
                dialog.close();
                app.showToast(editing ? "Doctor updated." : "Doctor created.");
                await renderDoctors();
            } catch (error) {
                document.getElementById("doctor-error").textContent = error.message;
                button.disabled = false;
            }
        });
    }

    async function deleteDoctor(button) {
        if (!(await app.confirmDialog(`Delete doctor ${button.dataset.doctorName}?`, "Delete doctor"))) {
            return;
        }
        button.disabled = true;
        try {
            const response = await app.request(`/api/admin/doctors/${button.dataset.deleteDoctor}`, { method: "DELETE" });
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.message || "Unable to delete doctor.");
            }
            app.showToast("Doctor deleted.");
            await renderDoctors();
        } catch (error) {
            fail(error);
            button.disabled = false;
        }
    }

    async function renderStaff() {
        loading("Loading staff accounts…");
        try {
            const accounts = await app.json("/api/admin/staff");
            content.innerHTML = `
                ${heading("Manage staff", "Create staff accounts, enable or disable access, and reset passwords.", `<button class="btn btn-primary" id="add-staff" type="button">Add staff account</button>`)}
                <article class="card">
                    ${accounts.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Username</th><th>Status</th><th>Actions</th></tr></thead><tbody>${accounts.map((account) => `<tr><td><strong>${escapeHtml(account.username)}</strong></td><td>${badge(account.enabled ? "ENABLED" : "DISABLED")}</td><td><div class="table-actions"><button class="btn btn-secondary" type="button" data-toggle-staff="${account.id}" data-username="${escapeHtml(account.username)}" data-enabled="${account.enabled}">${account.enabled ? "Disable" : "Enable"}</button><button class="btn btn-secondary" type="button" data-reset-staff="${account.id}" data-username="${escapeHtml(account.username)}">Reset password</button></div></td></tr>`).join("")}</tbody></table></div>` : empty("Create a staff account to grant portal access.", "No staff accounts")}
                </article>`;
            document.getElementById("add-staff").addEventListener("click", () => openStaffCreate());
            content.querySelectorAll("[data-toggle-staff]").forEach((button) => button.addEventListener("click", () => toggleStaff(button)));
            content.querySelectorAll("[data-reset-staff]").forEach((button) => button.addEventListener("click", () => resetStaffPassword(button)));
        } catch (error) {
            content.innerHTML = `${heading("Manage staff", "Create staff accounts and manage access.")}${empty(error.message, "Staff accounts could not load")}`;
            fail(error);
        }
    }

    function openStaffCreate() {
        const form = document.createElement("form");
        form.innerHTML = `
            <div class="field"><label for="staff-username">Username</label><input class="form-control" id="staff-username" name="username" maxlength="80" autocomplete="off" required></div>
            <div class="field" style="margin-top:16px"><label for="staff-password">Temporary password</label><input class="form-control" id="staff-password" name="password" type="password" minlength="8" maxlength="72" autocomplete="new-password" required><small class="text-muted">At least 8 characters. Share securely and ask the staff member to change it.</small></div>
            <p class="form-error" id="staff-error" role="alert"></p>
            <div class="modal-footer"><button class="btn btn-primary" type="submit">Create account</button></div>`;
        const dialog = app.openModal("Add staff account", form);
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const values = Object.fromEntries(new FormData(form).entries());
            if (!values.username.trim() || values.password.length < 8) {
                document.getElementById("staff-error").textContent = "Enter a username and a password of at least 8 characters.";
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            try {
                await app.json("/api/admin/staff", json("POST", { username: values.username.trim(), password: values.password }));
                dialog.close();
                app.showToast("Staff account created.");
                await renderStaff();
            } catch (error) {
                document.getElementById("staff-error").textContent = error.message;
                button.disabled = false;
            }
        });
    }

    async function toggleStaff(button) {
        const enabled = button.dataset.enabled !== "true";
        if (!enabled && !(await app.confirmDialog(`Disable ${button.dataset.username}'s staff access?`, "Disable staff account"))) {
            return;
        }
        button.disabled = true;
        try {
            await app.json(`/api/admin/staff/${button.dataset.toggleStaff}`, json("PUT", { enabled }));
            app.showToast(enabled ? "Staff account enabled." : "Staff account disabled.");
            await renderStaff();
        } catch (error) {
            fail(error);
            button.disabled = false;
        }
    }

    function resetStaffPassword(button) {
        const form = document.createElement("form");
        form.innerHTML = `
            <p>Set a new password for <strong>${escapeHtml(button.dataset.username)}</strong>.</p>
            <div class="field" style="margin-top:16px"><label for="staff-new-password">New password</label><input class="form-control" id="staff-new-password" name="password" type="password" minlength="8" maxlength="72" autocomplete="new-password" required></div>
            <p class="form-error" id="staff-reset-error" role="alert"></p>
            <div class="modal-footer"><button class="btn btn-primary" type="submit">Reset password</button></div>`;
        const dialog = app.openModal("Reset staff password", form);
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const password = new FormData(form).get("password");
            if (password.length < 8) {
                document.getElementById("staff-reset-error").textContent = "Password must be at least 8 characters.";
                return;
            }
            const submit = form.querySelector('button[type="submit"]');
            submit.disabled = true;
            try {
                await app.json(`/api/admin/staff/${button.dataset.resetStaff}`, json("PUT", { password }));
                dialog.close();
                app.showToast("Staff password reset.");
            } catch (error) {
                document.getElementById("staff-reset-error").textContent = error.message;
                submit.disabled = false;
            }
        });
    }

    async function renderReports() {
        content.innerHTML = `
            ${heading("Reports", "Preview a report for a date or date range and download it as CSV.")}
            <article class="card">
                <div class="form-layout">
                    <div class="field"><label for="report-type">Report</label><select class="form-control" id="report-type"><option value="daily-load">Daily load</option><option value="bed-occupancy">Bed occupancy</option><option value="appointments">Appointments</option></select></div>
                    <div class="field"><label for="report-start">Start date</label><input class="form-control" id="report-start" type="date" value="${today}"></div>
                    <div class="field"><label for="report-end">End date</label><input class="form-control" id="report-end" type="date" value="${today}"></div>
                </div>
                <div class="form-actions"><button class="btn btn-aqua" id="preview-report" type="button">Preview report</button><button class="btn btn-secondary" id="download-report" type="button">Download CSV</button></div>
                <div id="report-preview" style="margin-top:20px">${empty("Select a report and date range, then preview it.", "Report preview")}</div>
            </article>`;
        document.getElementById("preview-report").addEventListener("click", loadReport);
        document.getElementById("download-report").addEventListener("click", downloadReport);
        await loadReport();
    }

    function reportParams(format) {
        const startDate = document.getElementById("report-start").value;
        const endDate = document.getElementById("report-end").value;
        if (!startDate || !endDate || startDate > endDate) {
            throw new Error("Choose a valid date range with the start on or before the end.");
        }
        const params = new URLSearchParams({ startDate, endDate, format });
        return { path: `/api/admin/reports/${document.getElementById("report-type").value}`, params, startDate, endDate };
    }

    async function loadReport() {
        const target = document.getElementById("report-preview");
        target.innerHTML = `<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>Loading report…</span></div>`;
        try {
            const report = reportParams("json");
            const rows = await app.json(`${report.path}?${report.params}`);
            if (!rows.length) {
                target.innerHTML = empty("No records were found for the selected date range.", "No report data");
                return;
            }
            const keys = Object.keys(rows[0]);
            target.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr>${keys.map((key) => `<th>${escapeHtml(humanize(key))}</th>`).join("")}</tr></thead><tbody>${rows.slice(0, 100).map((row) => `<tr>${keys.map((key) => `<td>${escapeHtml(row[key] == null ? "—" : row[key])}</td>`).join("")}</tr>`).join("")}</tbody></table></div><p class="text-muted" style="margin-top:10px">Showing ${Math.min(rows.length, 100)} of ${rows.length} records.</p>`;
        } catch (error) {
            target.innerHTML = empty(error.message, "Report could not load");
            fail(error);
        }
    }

    function humanize(text) {
        return text.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase());
    }

    async function downloadReport() {
        try {
            const report = reportParams("csv");
            const response = await app.request(`${report.path}?${report.params}`);
            if (!response.ok) {
                const body = await response.json().catch(() => ({}));
                throw new Error(body.message || "Unable to download this report.");
            }
            const blob = await response.blob();
            const filename = `${document.getElementById("report-type").value}-${report.startDate}-${report.endDate}.csv`;
            const link = document.createElement("a");
            const url = URL.createObjectURL(blob);
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
            app.showToast("CSV download started.");
        } catch (error) {
            fail(error);
        }
    }

    async function renderAudit() {
        content.innerHTML = `
            ${heading("Audit log", "Review administrative and staff actions with filters and pagination.")}
            <article class="card">
                <div class="filter-row">
                    <div class="field"><label for="audit-user">User</label><input class="form-control" id="audit-user" type="search" placeholder="Username"></div>
                    <div class="field"><label for="audit-action">Action</label><input class="form-control" id="audit-action" type="search" placeholder="CREATE, UPDATE…"></div>
                    <div class="field"><label for="audit-date">Date</label><input class="form-control" id="audit-date" type="date"></div>
                    <button class="btn btn-aqua" id="audit-filter" type="button">Apply filters</button>
                </div>
                <div id="audit-table" aria-live="polite"></div>
                <div class="pagination" id="audit-pagination"></div>
            </article>`;
        document.getElementById("audit-filter").addEventListener("click", () => {
            auditFilters = {
                user: document.getElementById("audit-user").value.trim(),
                action: document.getElementById("audit-action").value.trim(),
                date: document.getElementById("audit-date").value
            };
            currentAuditPage = 0;
            loadAuditPage();
        });
        await loadAuditPage();
    }

    async function loadAuditPage() {
        const target = document.getElementById("audit-table");
        if (!target) {
            return;
        }
        target.innerHTML = `<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>Loading audit entries…</span></div>`;
        const params = new URLSearchParams({ page: String(currentAuditPage), size: "20" });
        Object.entries(auditFilters).forEach(([key, value]) => { if (value) params.set(key, value); });
        try {
            const page = await app.json(`/api/admin/audit-log?${params}`);
            target.innerHTML = page.content.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Date and time</th><th>User</th><th>Action</th><th>Entity</th><th>Entity ID</th></tr></thead><tbody>${page.content.map((row) => `<tr><td>${escapeHtml(String(row.timestamp).replace("T", " "))}</td><td>${escapeHtml(row.username)}</td><td>${badge(row.action)}</td><td>${escapeHtml(row.entity)}</td><td>${escapeHtml(row.entityId)}</td></tr>`).join("")}</tbody></table></div>` : empty("Try changing the filters or date.", "No audit entries");
            document.getElementById("audit-pagination").innerHTML = `
                <span class="pagination-info">Page ${page.totalPages ? page.number + 1 : 0} of ${page.totalPages} · ${page.totalElements} entries</span>
                <button class="btn btn-secondary" type="button" id="audit-prev"${page.first ? " disabled" : ""}>Previous</button>
                <button class="btn btn-secondary" type="button" id="audit-next"${page.last || page.totalPages === 0 ? " disabled" : ""}>Next</button>`;
            document.getElementById("audit-prev").addEventListener("click", () => {
                currentAuditPage = Math.max(0, currentAuditPage - 1);
                loadAuditPage();
            });
            document.getElementById("audit-next").addEventListener("click", () => {
                currentAuditPage = Math.min(page.totalPages - 1, currentAuditPage + 1);
                loadAuditPage();
            });
        } catch (error) {
            target.innerHTML = empty(error.message, "Audit log could not load");
            fail(error);
        }
    }

    async function initialize() {
        const user = await app.requireRole("ADMIN");
        if (!user) {
            return;
        }
        window.addEventListener("hashchange", () => render(window.location.hash.slice(1)));
        await render(window.location.hash.slice(1) || "dashboard");
    }

    document.addEventListener("DOMContentLoaded", initialize);
}());
