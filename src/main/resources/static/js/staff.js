(function () {
    "use strict";

    const app = window.HealthcareApp;
    const content = document.getElementById("staff-content");
    if (!app || !content) {
        return;
    }

    const api = {
        patients: "/api/staff/patients",
        doctors: "/api/staff/doctors",
        appointments: "/api/staff/appointments",
        occupancy: "/api/staff/beds/occupancy",
        activeBeds: "/api/staff/beds/allocations/active",
        load: "/api/staff/load-today"
    };
    const today = localDate();
    let activeView = "";
    let selectedBedPatient = null;
    let selectedSlot = null;
    let departments = [];
    let doctors = [];
    let freeSlots = [];
    let appointmentDate = today;
    let intakePatient = null;
    let intakeChoice = "";
    let intakeResult = null;
    let emergencyDepartment = null;
    let intakeBranchRequest = 0;
    let slotRequest = 0;

    function localDate() {
        const now = new Date();
        const offset = now.getTimezoneOffset() * 60000;
        return new Date(now.getTime() - offset).toISOString().slice(0, 10);
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

    function heading(title, subtitle) {
        return `<div class="page-heading"><div><p class="eyebrow">Staff Portal</p><h1>${escapeHtml(title)}</h1><p class="page-subtitle">${escapeHtml(subtitle)}</p></div></div>`;
    }

    function loading(label) {
        content.innerHTML = `${heading("Loading…", label)}<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>${escapeHtml(label)}</span></div>`;
    }

    function empty(message, title) {
        return `<div class="empty-state"><div><strong>${escapeHtml(title || "Nothing here yet")}</strong>${escapeHtml(message)}</div></div>`;
    }

    function errorMessage(error) {
        app.showToast(error.message || "The request could not be completed.", "error");
    }

    function selectedOption(value, current) {
        return String(value) === String(current) ? " selected" : "";
    }

    function departmentOptions(current) {
        return departments.map((department) =>
            `<option value="${department.departmentId}"${selectedOption(department.departmentId, current)}>${escapeHtml(department.departmentName)}</option>`
        ).join("");
    }

    function statusBadge(status) {
        const styles = {
            BOOKED: "",
            CHECKED_IN: " badge-ok",
            COMPLETED: " badge-ok",
            CANCELLED: " badge-shortage",
            NO_SHOW: " badge-warning"
        };
        return `<span class="badge${styles[status] || ""}">${escapeHtml(status.replaceAll("_", " "))}</span>`;
    }

    function appointmentTimeLabel(appointment) {
        if (appointment.slotTime) {
            return appointment.slotTime.slice(0, 5);
        }
        return appointment.type === "EMERGENCY" ? "Emergency" : "Queue";
    }

    function asJson(method, data) {
        return {
            method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        };
    }

    async function getDepartments() {
        departments = await app.json(api.occupancy);
        return departments;
    }

    function render(view) {
        const requestedView = view || "dashboard";
        const normalizedView = requestedView === "patients" || requestedView === "book" ? "intake" : requestedView;
        const validViews = new Set(["dashboard", "intake", "appointments", "beds", "load"]);
        activeView = validViews.has(normalizedView) ? normalizedView : "dashboard";
        document.querySelectorAll("[data-staff-nav]").forEach((link) => {
            if (link.dataset.staffNav === activeView) {
                link.setAttribute("aria-current", "page");
            } else {
                link.removeAttribute("aria-current");
            }
        });
        content.focus({ preventScroll: true });

        const renderers = {
            dashboard: renderDashboard,
            intake: renderIntake,
            appointments: renderAppointments,
            beds: renderBeds,
            load: renderLoad
        };
        renderers[activeView]();
    }

    async function renderDashboard() {
        loading("Loading today's appointments and department beds…");
        try {
            const [appointments, occupancy] = await Promise.all([
                app.json(`${api.appointments}?date=${today}`),
                app.json(api.occupancy)
            ]);
            content.innerHTML = `
                ${heading("Dashboard", "A quick view of today's patient care and department capacity.")}
                <section class="content-grid" aria-label="Today's overview">
                    <article class="card grid-span-4">
                        <div class="stat-label">Today's appointments</div>
                        <div class="stat-value">${appointments.length}</div>
                        <p class="text-muted">All appointment statuses</p>
                    </article>
                    <article class="card grid-span-8">
                        <div class="card-header"><h2>Beds available by department</h2><a href="#beds">Allocate a bed</a></div>
                        ${occupancy.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Department</th><th>Available</th><th>Occupied</th><th>Maintenance</th></tr></thead><tbody>${occupancy.map((item) => `<tr><td>${escapeHtml(item.departmentName)}</td><td><span class="badge badge-ok">${item.available}</span></td><td>${item.occupied}</td><td>${item.maintenance}</td></tr>`).join("")}</tbody></table></div>` : empty("No departments are configured.", "No department data")}
                    </article>
                    <article class="card grid-span-12">
                        <div class="card-header"><h2>Quick actions</h2></div>
                        <div class="quick-actions">
                            <a class="btn btn-aqua" href="#intake">Start patient visit</a>
                            <a class="btn btn-secondary" href="#beds">Allocate bed</a>
                        </div>
                    </article>
                    <article class="card grid-span-12">
                        <div class="card-header"><h2>Today's appointments</h2><a href="#appointments">View all</a></div>
                        ${appointments.length ? `<div class="table-wrap"><table class="table"><thead><tr><th>Time</th><th>Token</th><th>Patient</th><th>Doctor</th><th>Department</th><th>Status</th></tr></thead><tbody>${appointments.slice(0, 6).map((appointment) => `<tr><td>${escapeHtml(appointmentTimeLabel(appointment))}</td><td>${appointment.tokenNumber}</td><td>${escapeHtml(appointment.patientName)}</td><td>${escapeHtml(appointment.doctorName)}</td><td>${escapeHtml((occupancy.find((d) => d.departmentId === appointment.departmentId) || {}).departmentName || "—")}</td><td>${statusBadge(appointment.status)}</td></tr>`).join("")}</tbody></table></div>` : empty("There are no appointments scheduled for today.", "No appointments")}
                    </article>
                </section>`;
        } catch (error) {
            content.innerHTML = `${heading("Dashboard", "A quick view of today's patient care and department capacity.")}${empty(error.message, "Dashboard could not load")}`;
            errorMessage(error);
        }
    }

    async function renderIntake() {
        loading("Preparing patient intake…");
        selectedSlot = null;
        freeSlots = [];
        try {
            await getDepartments();
            emergencyDepartment = departments.find((department) => /emergency/i.test(department.departmentName)) || null;
            content.innerHTML = `
                ${heading("New patient visit", "Register the patient's basic details, then choose the care pathway.")}
                <section class="section-stack">
                    <article class="card">
                        <div class="card-header"><h2>1. Patient details</h2></div>
                        ${intakePatient ? `
                            <div class="inline-notice"><strong>${escapeHtml(intakePatient.name)}</strong> · Age ${intakePatient.age} · ${escapeHtml(intakePatient.gender)} · ${escapeHtml(intakePatient.phone)}</div>
                            <div class="form-actions"><button class="btn btn-secondary" type="button" id="intake-start-over">Start another patient</button></div>
                        ` : `
                            <form id="intake-patient-form" novalidate>
                                <div class="form-layout">
                                    <div class="field"><label for="intake-name">Full name</label><input class="form-control" id="intake-name" name="name" maxlength="120" required></div>
                                    <div class="field"><label for="intake-age">Age</label><input class="form-control" id="intake-age" name="age" type="number" min="0" max="120" required></div>
                                    <div class="field"><label for="intake-gender">Gender</label><input class="form-control" id="intake-gender" name="gender" maxlength="30" required></div>
                                    <div class="field"><label for="intake-phone">Phone number</label><input class="form-control" id="intake-phone" name="phone" type="tel" inputmode="numeric" maxlength="10" required></div>
                                </div>
                                <p id="intake-patient-error" class="form-error" role="alert"></p>
                                <div class="form-actions"><button class="btn btn-aqua" type="submit">Save details and continue</button></div>
                            </form>
                            <details class="patient-lookup">
                                <summary>Use an existing patient</summary>
                                <form id="intake-existing-search-form" class="filter-row">
                                    <div class="field"><label for="intake-existing-search">Search by name or phone</label><input class="form-control" id="intake-existing-search" type="search" required></div>
                                    <button class="btn btn-secondary" type="submit">Search</button>
                                </form>
                                <div id="intake-existing-search-results" aria-live="polite"></div>
                            </details>
                        `}
                    </article>
                    ${intakePatient
                        ? intakeResult
                            ? `<article class="card"><div id="intake-branch"></div></article>`
                            : renderIntakeChoices()
                        : ""}
                </section>`;

            const startOver = document.getElementById("intake-start-over");
            if (startOver) {
                startOver.addEventListener("click", resetIntake);
            }
            const patientForm = document.getElementById("intake-patient-form");
            if (patientForm) {
                patientForm.addEventListener("submit", submitIntakePatient);
                document.getElementById("intake-existing-search-form").addEventListener("submit", async (event) => {
                    event.preventDefault();
                    const query = document.getElementById("intake-existing-search").value;
                    await searchPatients(query, "intake-existing-search-results", (patient) => {
                        intakePatient = patient;
                        intakeChoice = "";
                        intakeResult = null;
                        renderIntake();
                    });
                });
            }
            document.querySelectorAll("[data-intake-choice]").forEach((button) => {
                button.addEventListener("click", () => chooseIntakePath(button.dataset.intakeChoice));
            });
            if (intakeChoice && intakePatient && !intakeResult) {
                await renderIntakeBranch(intakeChoice);
            }
            if (intakeResult) {
                renderIntakeResult();
            }
        } catch (error) {
            content.innerHTML = `${heading("New patient visit", "Register the patient's basic details, then choose the care pathway.")}${empty(error.message, "Patient intake could not load")}`;
            errorMessage(error);
        }
    }

    function renderIntakeChoices() {
        return `
            <article class="card">
                <div class="card-header"><h2>2. Choose a care pathway</h2></div>
                <div class="intake-options" role="group" aria-label="Choose a care pathway">
                    <button class="intake-option" type="button" data-intake-choice="general" aria-pressed="${intakeChoice === "general"}"><strong>General doctor</strong><span>Book an appointment for a normal illness.</span></button>
                    <button class="intake-option" type="button" data-intake-choice="emergency" aria-pressed="${intakeChoice === "emergency"}"><strong>Emergency</strong><span>Allocate an available room in the Emergency department.</span></button>
                    <button class="intake-option" type="button" data-intake-choice="specialist" aria-pressed="${intakeChoice === "specialist"}"><strong>Specialist doctor</strong><span>Choose an available doctor by specialty/department.</span></button>
                </div>
                <div id="intake-branch" class="section-stack"></div>
            </article>`;
    }

    async function submitIntakePatient(event) {
        event.preventDefault();
        const form = event.currentTarget;
        const data = new FormData(form);
        const values = {
            name: String(data.get("name") || "").trim(),
            age: Number(data.get("age")),
            gender: String(data.get("gender") || "").trim(),
            phone: String(data.get("phone") || "").trim()
        };
        if (!values.name || !Number.isInteger(values.age) || values.age < 0 || values.age > 120
                || !values.gender || !/^\d{10}$/.test(values.phone)) {
            document.getElementById("intake-patient-error").textContent =
                "Enter a name and gender, an age from 0 to 120, and a 10-digit phone number.";
            return;
        }
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        button.textContent = "Saving…";
        try {
            intakePatient = await app.json(api.patients, asJson("POST", values));
            app.showToast("Patient registered. Choose a care pathway.");
            await renderIntake();
        } catch (error) {
            document.getElementById("intake-patient-error").textContent = error.message;
            errorMessage(error);
            button.disabled = false;
            button.textContent = "Save details and continue";
        }
    }

    function chooseIntakePath(choice) {
        intakeChoice = choice;
        selectedSlot = null;
        freeSlots = [];
        document.querySelectorAll("[data-intake-choice]").forEach((button) => {
            button.setAttribute("aria-pressed", String(button.dataset.intakeChoice === choice));
        });
        renderIntakeBranch(choice);
    }

    async function renderIntakeBranch(choice) {
        const requestId = ++intakeBranchRequest;
        const branch = document.getElementById("intake-branch");
        if (!branch || !intakePatient) {
            return;
        }
        selectedSlot = null;
        if (choice === "emergency") {
            const emergency = emergencyDepartment;
            branch.innerHTML = `
                <article class="card">
                    <div class="card-header"><h3>Emergency room allocation</h3></div>
                    ${!emergency
                        ? empty("An Emergency department is not configured. Ask an administrator to configure it.", "Emergency department unavailable")
                        : emergency.available > 0
                            ? `<p>${emergency.available} room${emergency.available === 1 ? "" : "s"} currently available in ${escapeHtml(emergency.departmentName)}.</p><p id="intake-action-error" class="form-error" role="alert"></p><div class="form-actions"><button class="btn btn-aqua" type="button" id="intake-emergency-submit">Allocate emergency room</button></div>`
                            : empty("There are currently no free emergency rooms.", "No emergency rooms available")}</article>`;
            const allocateButton = document.getElementById("intake-emergency-submit");
            if (allocateButton) {
                allocateButton.addEventListener("click", submitEmergencyAllocation);
            }
            return;
        }
        if (choice !== "general" && choice !== "specialist") {
            branch.innerHTML = "";
            return;
        }

        const branchTitle = choice === "general" ? "General Ward doctor allocation" : "Choose a specialist";
        branch.innerHTML = `<article class="card"><div class="card-header"><h3>${branchTitle}</h3></div><p class="text-muted">Loading available doctors…</p></article>`;
        try {
            if (choice === "general") {
                doctors = await app.json(`/api/staff/doctors/general-ward?date=${today}`);
                if (requestId !== intakeBranchRequest || intakeChoice !== choice || !branch.isConnected) {
                    return;
                }
                if (!doctors.length) {
                    branch.innerHTML = `<article class="card"><div class="card-header"><h3>${branchTitle}</h3></div>${empty("No on-duty doctors are available in the General Ward today.", "No doctors available")}</article>`;
                    return;
                }
                const hasCapacity = doctors.some((doctor) => doctor.patientsToday < doctor.patientLimit);
                branch.innerHTML = `
                    <article class="card">
                        <div class="card-header"><h3>${branchTitle}</h3></div>
                        <p class="text-muted">Choose an on-duty doctor with room under the daily limit. Patient counts include today's active and completed visits.</p>
                        <form id="intake-appointment-form">
                            <div class="field"><label for="intake-doctor">General Ward doctor and patient load</label><select class="form-control" id="intake-doctor" required><option value="">Choose a doctor</option>${doctors.map((doctor) => {
                                const full = doctor.patientsToday >= doctor.patientLimit;
                                return `<option value="${doctor.doctorId}"${full ? " disabled" : ""}>${escapeHtml(doctor.doctorName)} · ${doctor.patientsToday}/${doctor.patientLimit} patients today${full ? " · At capacity" : ""}</option>`;
                            }).join("")}</select></div>
                            ${hasCapacity ? "" : `<p class="form-error">All on-duty General Ward doctors have reached their daily patient limit.</p>`}
                            <p id="intake-action-error" class="form-error" role="alert"></p>
                            <div class="form-actions"><button class="btn btn-aqua" id="intake-appointment-submit" type="submit"${hasCapacity ? "" : " disabled"}>Assign to doctor</button></div>
                        </form>
                    </article>`;
                document.getElementById("intake-appointment-form").addEventListener("submit", submitIntakeAppointment);
                return;
            }

            const onDutyDoctors = await app.json(api.doctors);
            if (requestId !== intakeBranchRequest || intakeChoice !== choice || !branch.isConnected) {
                return;
            }
            doctors = onDutyDoctors.filter((doctor) =>
                !/general/i.test(doctor.departmentName) && !/emergency/i.test(doctor.departmentName));
            if (!doctors.length) {
                branch.innerHTML = `<article class="card"><div class="card-header"><h3>${branchTitle}</h3></div>${empty(choice === "general"
                    ? "No on-duty doctors are configured in a General department."
                    : "No on-duty specialist doctors are currently available.", "No doctors available")}</article>`;
                return;
            }
            branch.innerHTML = `
                <article class="card">
                    <div class="card-header"><h3>${branchTitle}</h3></div>
                    <form id="intake-appointment-form">
                        <div class="form-layout">
                            <div class="field"><label for="intake-doctor">${choice === "general" ? "General doctor" : "Available specialist doctor"}</label><select class="form-control" id="intake-doctor" required><option value="">Choose a doctor</option>${doctors.map((doctor) => `<option value="${doctor.id}">${escapeHtml(doctor.name)} · ${escapeHtml(doctor.departmentName)} · ${escapeHtml(doctor.workStart.slice(0, 5))}–${escapeHtml(doctor.workEnd.slice(0, 5))}</option>`).join("")}</select></div>
                            <div class="field"><label for="intake-date">Appointment date</label><input class="form-control" id="intake-date" type="date" min="${today}" value="${today}" required></div>
                        </div>
                        <div><div class="card-header" style="margin-top:24px"><h3>Available appointment times</h3></div><p id="intake-slot-help" class="text-muted">Choose a doctor and date to see available times.</p><div id="intake-slot-grid" class="slot-grid" aria-live="polite"></div></div>
                        <p id="intake-action-error" class="form-error" role="alert"></p>
                        <div class="form-actions"><button class="btn btn-aqua" id="intake-appointment-submit" type="submit">Confirm appointment</button></div>
                    </form>
                </article>`;
            document.getElementById("intake-doctor").addEventListener("change", loadSlots);
            document.getElementById("intake-date").addEventListener("change", loadSlots);
            document.getElementById("intake-appointment-form").addEventListener("submit", submitIntakeAppointment);
        } catch (error) {
            if (requestId !== intakeBranchRequest || intakeChoice !== choice || !branch.isConnected) {
                return;
            }
            branch.innerHTML = `<article class="card">${empty(error.message, "Doctors could not load")}</article>`;
            errorMessage(error);
        }
    }

    async function submitEmergencyAllocation(event) {
        const button = event.currentTarget;
        const errorTarget = document.getElementById("intake-action-error");
        button.disabled = true;
        button.textContent = "Allocating…";
        try {
            const allocation = await app.json("/api/staff/beds/allocate", asJson("POST", {
                patientId: intakePatient.id,
                departmentId: emergencyDepartment.departmentId
            }));
            intakeResult = `Emergency room ${allocation.bedNumber} allocated in ${allocation.departmentName} for ${allocation.patientName}.`;
            app.showToast("Emergency room allocated.");
            renderIntakeResult();
        } catch (error) {
            errorTarget.textContent = error.message;
            errorMessage(error);
            button.disabled = false;
            button.textContent = "Allocate emergency room";
        }
    }

    async function submitIntakeAppointment(event) {
        event.preventDefault();
        const doctorId = document.getElementById("intake-doctor").value;
        const errorTarget = document.getElementById("intake-action-error");
        errorTarget.textContent = "";
        const generalWard = intakeChoice === "general";
        const date = generalWard ? today : document.getElementById("intake-date").value;
        if (!doctorId || !date || date < today || (!generalWard && !selectedSlot)) {
            errorTarget.textContent = !doctorId
                ? "Choose an available doctor."
                : !date || date < today
                    ? "Choose today or a future date."
                    : "Choose an available appointment time.";
            return;
        }
        const button = document.getElementById("intake-appointment-submit");
        button.disabled = true;
        button.textContent = "Booking…";
        try {
            const request = {
                patientId: intakePatient.id,
                doctorId: Number(doctorId),
                date,
                type: "WALK_IN"
            };
            if (!generalWard) {
                request.slotTime = selectedSlot;
            }
            const appointment = await app.json(api.appointments, asJson("POST", request));
            intakeResult = generalWard
                ? `General Ward token ${appointment.tokenNumber} assigned to ${appointment.patientName} with ${appointment.doctorName}.`
                : `Appointment booked for ${appointment.patientName} with ${appointment.doctorName}.`;
            app.showToast("Appointment booked successfully.");
            renderIntakeResult();
            showAppointmentSlip(appointment);
        } catch (error) {
            errorTarget.textContent = error.message;
            errorMessage(error);
            if (!generalWard) {
                await loadSlots();
            }
        } finally {
            button.disabled = false;
            button.textContent = "Confirm appointment";
        }
    }

    function renderIntakeResult() {
        const branch = document.getElementById("intake-branch");
        if (!branch || !intakeResult) {
            return;
        }
        branch.innerHTML = `<article class="card"><div class="card-header"><h3>Visit ready</h3></div><p>${escapeHtml(intakeResult)}</p><div class="form-actions"><button class="btn btn-aqua" type="button" id="intake-start-next">Start another patient</button></div></article>`;
        document.getElementById("intake-start-next").addEventListener("click", resetIntake);
    }

    function resetIntake() {
        intakePatient = null;
        intakeChoice = "";
        intakeResult = null;
        intakeBranchRequest++;
        slotRequest++;
        selectedSlot = null;
        freeSlots = [];
        renderIntake();
    }

    async function searchPatients(query, targetId, onSelect) {
        const target = document.getElementById(targetId);
        if (!target) {
            return;
        }
        if (!String(query || "").trim()) {
            target.innerHTML = empty("Enter a name or phone number to search.", "Patient search");
            return;
        }
        target.innerHTML = `<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>Searching patients…</span></div>`;
        try {
            const patients = await app.json(`${api.patients}?search=${encodeURIComponent(String(query).trim())}`);
            if (!patients.length) {
                target.innerHTML = empty("No patient matches that name or phone.", "No results");
                return;
            }
            target.innerHTML = `<div class="search-results">${patients.map((patient) =>
                `<button class="search-result" type="button" data-patient-id="${patient.id}" aria-pressed="false"><span><strong>${escapeHtml(patient.name)}</strong><small>Age ${patient.age} · ${escapeHtml(patient.phone)} · ID ${patient.id}</small></span><span class="badge">Select</span></button>`
            ).join("")}</div>`;
            target.querySelectorAll("[data-patient-id]").forEach((button) => {
                button.addEventListener("click", () => {
                    const patient = patients.find((item) => String(item.id) === button.dataset.patientId);
                    target.querySelectorAll("[data-patient-id]").forEach((option) => option.setAttribute("aria-pressed", "false"));
                    button.setAttribute("aria-pressed", "true");
                    onSelect(patient);
                });
            });
        } catch (error) {
            target.innerHTML = empty(error.message, "Search failed");
            errorMessage(error);
        }
    }

    function pickerMarkup(label, prefix) {
        return `<div class="patient-picker"><div class="field"><label for="${prefix}-search">${escapeHtml(label)}</label><input class="form-control" id="${prefix}-search" type="search" placeholder="Search by name or phone" autocomplete="off"></div><div class="form-actions"><button class="btn btn-secondary" type="button" id="${prefix}-quick-register">Quick register patient</button></div><div id="${prefix}-selected" class="inline-notice" role="status">No patient selected.</div><div id="${prefix}-results"></div></div>`;
    }

    function wirePatientPicker(prefix, onSelect) {
        const search = document.getElementById(`${prefix}-search`);
        let timer;
        search.addEventListener("input", () => {
            window.clearTimeout(timer);
            timer = window.setTimeout(() => searchPatients(search.value, `${prefix}-results`, onSelect), 250);
        });
        document.getElementById(`${prefix}-quick-register`).addEventListener("click", () => openQuickRegister(onSelect));
    }

    function openQuickRegister(onSelect) {
        const form = document.createElement("form");
        form.innerHTML = `
            <div class="form-layout">
                <div class="field"><label for="quick-name">Full name</label><input class="form-control" id="quick-name" name="name" maxlength="120" required></div>
                <div class="field"><label for="quick-age">Age</label><input class="form-control" id="quick-age" name="age" type="number" min="0" max="120" required></div>
                <div class="field"><label for="quick-gender">Gender</label><input class="form-control" id="quick-gender" name="gender" maxlength="30" required></div>
                <div class="field"><label for="quick-phone">Phone</label><input class="form-control" id="quick-phone" name="phone" inputmode="numeric" maxlength="10" required></div>
            </div>
            <p class="form-error" id="quick-register-error" role="alert"></p>
            <div class="modal-footer"><button class="btn btn-aqua" type="submit">Register and select</button></div>`;
        const dialog = app.openModal("Quick register patient", form);
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const values = Object.fromEntries(new FormData(form).entries());
            const age = Number(values.age);
            values.name = String(values.name).trim();
            values.age = age;
            values.gender = String(values.gender).trim();
            values.phone = String(values.phone).trim();
            if (!values.name || !values.gender || !Number.isInteger(age)
                    || age < 0 || age > 120 || !/^\d{10}$/.test(values.phone)) {
                document.getElementById("quick-register-error").textContent =
                    "Enter a name and gender, an age from 0 to 120, and a 10-digit phone number.";
                return;
            }
            const button = form.querySelector('button[type="submit"]');
            button.disabled = true;
            button.textContent = "Registering…";
            try {
                const patient = await app.json(api.patients, asJson("POST", values));
                onSelect(patient);
                dialog.close();
                app.showToast("Patient registered and selected.");
            } catch (error) {
                document.getElementById("quick-register-error").textContent = error.message;
                button.disabled = false;
                button.textContent = "Register and select";
            }
        });
    }

    function setPatientSelection(kind, patient) {
        selectedBedPatient = patient;
        const selection = document.getElementById(`${kind}-selected`);
        if (selection) {
            selection.innerHTML = `<strong>Selected:</strong> ${escapeHtml(patient.name)} · ${escapeHtml(patient.phone)} (ID ${patient.id})`;
        }
    }

    function slotsForDoctor(doctor) {
        const slots = [];
        let start = timeToMinutes(doctor.workStart);
        const end = timeToMinutes(doctor.workEnd);
        while (start < end) {
            slots.push(minutesToTime(start));
            start += doctor.slotMinutes;
        }
        return slots;
    }

    function timeToMinutes(time) {
        const [hour, minute] = time.split(":").map(Number);
        return hour * 60 + minute;
    }

    function minutesToTime(minutes) {
        return `${String(Math.floor(minutes / 60)).padStart(2, "0")}:${String(minutes % 60).padStart(2, "0")}:00`;
    }

    async function loadSlots() {
        const requestId = ++slotRequest;
        const doctorSelect = document.getElementById("intake-doctor");
        const dateInput = document.getElementById("intake-date");
        const doctorId = doctorSelect && doctorSelect.value;
        appointmentDate = dateInput && dateInput.value;
        selectedSlot = null;
        const grid = document.getElementById("intake-slot-grid");
        const help = document.getElementById("intake-slot-help");
        if (!grid || !help) {
            return;
        }
        grid.innerHTML = "";
        if (!doctorId || !appointmentDate) {
            help.textContent = "Choose a doctor and date to see the schedule.";
            return;
        }
        help.innerHTML = `<span class="spinner" aria-hidden="true"></span> Loading availability…`;
        try {
            freeSlots = await app.json(`${api.doctors}/${doctorId}/slots?date=${encodeURIComponent(appointmentDate)}`);
            if (requestId !== slotRequest || !grid.isConnected) {
                return;
            }
            const doctor = doctors.find((item) => String(item.id) === doctorId);
            if (!doctor) {
                help.textContent = "The selected doctor is no longer available. Choose another doctor.";
                return;
            }
            const available = new Set(freeSlots.map((time) => time.slice(0, 8)));
            const currentMinutes = timeToMinutes(new Date().toTimeString().slice(0, 5) + ":00");
            const allSlots = slotsForDoctor(doctor);
            if (!allSlots.length) {
                help.textContent = "There are no bookable slots for this doctor's schedule.";
                return;
            }
            help.textContent = "Unavailable and past times are disabled.";
            grid.innerHTML = allSlots.map((slot) => {
                const isPast = appointmentDate === today && timeToMinutes(slot) <= currentMinutes;
                const isFree = available.has(slot.slice(0, 8)) && !isPast;
                const label = slot.slice(0, 5);
                return `<button type="button" class="slot-button" data-slot="${slot}" aria-pressed="false"${isFree ? "" : " disabled"}>${label}${isFree ? "" : `<span class="visually-hidden"> unavailable</span>`}</button>`;
            }).join("");
            grid.querySelectorAll("[data-slot]").forEach((button) => {
                button.addEventListener("click", () => {
                    selectedSlot = button.dataset.slot;
                    grid.querySelectorAll("[data-slot]").forEach((slotButton) => slotButton.setAttribute("aria-pressed", "false"));
                    button.setAttribute("aria-pressed", "true");
                });
            });
            if (!freeSlots.length) {
                help.textContent = "No available slots remain for this doctor on this date.";
            }
        } catch (error) {
            if (requestId !== slotRequest || !grid.isConnected) {
                return;
            }
            help.textContent = error.message || "Unable to load slots.";
            errorMessage(error);
        }
    }

    function showAppointmentSlip(appointment) {
        const department = departments.find((item) => item.departmentId === appointment.departmentId);
        const slip = document.createElement("div");
        slip.className = "print-slip";
        slip.innerHTML = `
            <p class="eyebrow">Smart Healthcare · Appointment slip</p>
            <h2>${escapeHtml(appointment.type === "EMERGENCY" ? "Emergency appointment" : appointment.type === "WALK_IN" && !appointment.slotTime ? "General Ward queue" : "Walk-in appointment")}</h2>
            <div class="print-token">Token ${appointment.tokenNumber}</div>
            <p><strong>Patient:</strong> ${escapeHtml(appointment.patientName)}</p>
            <p><strong>Department:</strong> ${escapeHtml(department ? department.departmentName : "—")}</p>
            <p><strong>Doctor:</strong> ${escapeHtml(appointment.doctorName)}</p>
            <p><strong>Date:</strong> ${escapeHtml(appointment.date)}</p>
            <p><strong>Time:</strong> ${appointment.slotTime ? escapeHtml(appointment.slotTime.slice(0, 5)) : escapeHtml(appointment.type === "EMERGENCY" ? "Emergency queue" : "Queue allocation")}</p>
            <p><strong>Status:</strong> ${escapeHtml(appointment.status)}</p>`;
        const wrapper = document.createElement("div");
        wrapper.appendChild(slip);
        const dialog = app.openModal("Appointment confirmed", wrapper);
        const printButton = document.createElement("button");
        printButton.className = "btn btn-aqua";
        printButton.type = "button";
        printButton.textContent = "Print slip";
        printButton.addEventListener("click", () => printSlip(slip.outerHTML));
        dialog.querySelector(".modal-header").appendChild(printButton);
        const footer = document.createElement("div");
        footer.className = "modal-footer";
        const close = document.createElement("button");
        close.className = "btn btn-secondary";
        close.type = "button";
        close.textContent = "Done";
        close.addEventListener("click", () => {
            dialog.close();
            window.location.hash = "appointments";
        });
        footer.appendChild(close);
        dialog.querySelector(".modal").appendChild(footer);
    }

    function printSlip(markup) {
        const printWindow = window.open("", "_blank", "width=640,height=720");
        if (!printWindow) {
            app.showToast("Allow pop-ups to print the appointment slip.", "error");
            return;
        }
        printWindow.document.write(`<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Appointment slip</title><link rel="stylesheet" href="/css/style.css"><style>body{padding:24px;background:#fff}.print-slip{margin:auto}</style></head><body>${markup}<script>window.addEventListener("load",function(){window.print()})<\/script></body></html>`);
        printWindow.document.close();
    }

    async function renderAppointments() {
        loading("Loading today's appointments…");
        try {
            await getDepartments();
            content.innerHTML = `
                ${heading("Today's appointments", "Review today's bookings and update patient visit status.")}
                <article class="card">
                    <div class="filter-row">
                        <div class="field"><label for="appointment-department-filter">Department</label><select class="form-control" id="appointment-department-filter"><option value="">All departments</option>${departmentOptions("")}</select></div>
                        <div class="field"><label for="appointment-status-filter">Status</label><select class="form-control" id="appointment-status-filter"><option value="">All statuses</option><option>BOOKED</option><option>CHECKED_IN</option><option>COMPLETED</option><option>CANCELLED</option><option>NO_SHOW</option></select></div>
                        <button class="btn btn-aqua" type="button" id="refresh-appointments">Apply filters</button>
                    </div>
                    <div id="appointments-table" aria-live="polite"></div>
                </article>`;
            document.getElementById("refresh-appointments").addEventListener("click", loadAppointments);
            document.getElementById("appointment-department-filter").addEventListener("change", loadAppointments);
            document.getElementById("appointment-status-filter").addEventListener("change", loadAppointments);
            await loadAppointments();
        } catch (error) {
            content.innerHTML = `${heading("Today's appointments", "Review today's bookings and update patient visit status.")}${empty(error.message, "Appointments could not load")}`;
            errorMessage(error);
        }
    }

    async function loadAppointments() {
        const target = document.getElementById("appointments-table");
        if (!target) {
            return;
        }
        target.innerHTML = `<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>Loading appointments…</span></div>`;
        const params = new URLSearchParams({ date: today });
        const departmentId = document.getElementById("appointment-department-filter").value;
        const status = document.getElementById("appointment-status-filter").value;
        if (departmentId) {
            params.set("departmentId", departmentId);
        }
        if (status) {
            params.set("status", status);
        }
        try {
            const appointments = await app.json(`${api.appointments}?${params}`);
            if (!appointments.length) {
                target.innerHTML = empty("No appointments match these filters.", "No appointments");
                return;
            }
            target.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Time</th><th>Token</th><th>Patient</th><th>Doctor</th><th>Department</th><th>Status</th><th>Actions</th></tr></thead><tbody>${appointments.map((appointment) => {
                let actions = "";
                if (appointment.status === "BOOKED") {
                    actions = `<button class="btn btn-aqua" data-status-id="${appointment.id}" data-next-status="CHECKED_IN">Check in</button> <button class="btn btn-secondary" data-status-id="${appointment.id}" data-next-status="CANCELLED">Cancel</button>`;
                } else if (appointment.status === "CHECKED_IN") {
                    actions = `<button class="btn btn-aqua" data-status-id="${appointment.id}" data-next-status="COMPLETED">Complete</button>`;
                } else {
                    actions = `<span class="text-muted">—</span>`;
                }
                const department = departments.find((item) => item.departmentId === appointment.departmentId);
                return `<tr><td>${escapeHtml(appointmentTimeLabel(appointment))}</td><td>${appointment.tokenNumber}</td><td>${escapeHtml(appointment.patientName)}</td><td>${escapeHtml(appointment.doctorName)}</td><td>${escapeHtml(department ? department.departmentName : "—")}</td><td>${statusBadge(appointment.status)}</td><td><div class="quick-actions">${actions}</div></td></tr>`;
            }).join("")}</tbody></table></div>`;
            target.querySelectorAll("[data-status-id]").forEach((button) => {
                button.addEventListener("click", () => updateAppointmentStatus(button));
            });
        } catch (error) {
            target.innerHTML = empty(error.message, "Appointments could not load");
            errorMessage(error);
        }
    }

    async function updateAppointmentStatus(button) {
        const status = button.dataset.nextStatus;
        if (status === "CANCELLED" && !(await app.confirmDialog("Cancel this appointment? The time slot will become available again.", "Cancel appointment"))) {
            return;
        }
        button.disabled = true;
        try {
            await app.json(`${api.appointments}/${button.dataset.statusId}/status`, asJson("PUT", { status }));
            app.showToast(`Appointment ${status.toLowerCase().replaceAll("_", " ")}.`);
            await loadAppointments();
        } catch (error) {
            errorMessage(error);
            button.disabled = false;
        }
    }

    async function renderBeds() {
        loading("Loading bed availability and current admissions…");
        selectedBedPatient = null;
        try {
            await getDepartments();
            content.innerHTML = `
                ${heading("Allocate bed", "Choose a patient and department. The system assigns the next available bed.")}
                <section class="content-grid">
                    <article class="card grid-span-6">
                        <div class="card-header"><h2>Allocate a bed</h2></div>
                        ${pickerMarkup("Patient", "bed")}
                        <div class="field" style="margin-top:18px"><label for="bed-department">Department</label><select class="form-control" id="bed-department"><option value="">Choose a department</option>${departmentOptions("")}</select></div>
                        <div class="card-header" style="margin-top:20px"><h3>Available beds</h3></div>
                        <div id="available-bed-grid" class="bed-grid">${empty("Choose a department to see available beds.", "No department selected")}</div>
                        <div class="form-actions"><button class="btn btn-aqua" id="allocate-bed" type="button" disabled>Confirm allocation</button></div>
                    </article>
                    <article class="card grid-span-6">
                        <div class="card-header"><h2>Current admissions</h2><span class="badge" id="active-bed-count">Loading…</span></div>
                        <div id="active-bed-list" aria-live="polite"><div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>Loading admissions…</span></div></div>
                    </article>
                </section>`;
            wirePatientPicker("bed", (patient) => setPatientSelection("bed", patient));
            document.getElementById("bed-department").addEventListener("change", loadAvailableBeds);
            document.getElementById("allocate-bed").addEventListener("click", allocateBed);
            await loadActiveBeds();
        } catch (error) {
            content.innerHTML = `${heading("Allocate bed", "Choose a patient and department.")}${empty(error.message, "Bed allocation could not load")}`;
            errorMessage(error);
        }
    }

    async function loadAvailableBeds() {
        const departmentId = document.getElementById("bed-department").value;
        const target = document.getElementById("available-bed-grid");
        const button = document.getElementById("allocate-bed");
        button.disabled = true;
        if (!departmentId) {
            target.innerHTML = empty("Choose a department to see available beds.", "No department selected");
            return;
        }
        target.innerHTML = `<div class="empty-state"><span class="spinner" aria-hidden="true"></span><span>Loading available beds…</span></div>`;
        try {
            const beds = await app.json(`/api/staff/beds/available?departmentId=${encodeURIComponent(departmentId)}`);
            if (!beds.length) {
                target.innerHTML = empty("Choose another department or wait for a discharge.", "No beds available");
                return;
            }
            target.innerHTML = beds.map((bed) => `<div class="bed-tile"><strong>${escapeHtml(bed.bedNumber)}</strong><small>Available</small></div>`).join("");
            button.disabled = false;
        } catch (error) {
            target.innerHTML = empty(error.message, "Availability could not load");
            errorMessage(error);
        }
    }

    async function allocateBed() {
        if (!selectedBedPatient) {
            app.showToast("Select or register a patient first.", "error");
            return;
        }
        const departmentId = document.getElementById("bed-department").value;
        if (!departmentId) {
            app.showToast("Choose a department.", "error");
            return;
        }
        const department = departments.find((item) => String(item.departmentId) === departmentId);
        if (!(await app.confirmDialog(`Allocate the next available bed in ${department ? department.departmentName : "this department"} to ${selectedBedPatient.name}?`, "Confirm bed allocation"))) {
            return;
        }
        const button = document.getElementById("allocate-bed");
        button.disabled = true;
        button.innerHTML = `<span class="spinner" aria-hidden="true"></span> Allocating…`;
        try {
            const allocation = await app.json("/api/staff/beds/allocate", asJson("POST", {
                patientId: Number(selectedBedPatient.id),
                departmentId: Number(departmentId)
            }));
            app.showToast(`Bed ${allocation.bedNumber} allocated to ${allocation.patientName}.`);
            selectedBedPatient = null;
            document.getElementById("bed-selected").textContent = "No patient selected.";
            await Promise.all([loadAvailableBeds(), loadActiveBeds()]);
        } catch (error) {
            errorMessage(error);
            await loadAvailableBeds();
        } finally {
            button.textContent = "Confirm allocation";
        }
    }

    async function loadActiveBeds() {
        const target = document.getElementById("active-bed-list");
        try {
            const allocations = await app.json(api.activeBeds);
            document.getElementById("active-bed-count").textContent = `${allocations.length} admitted`;
            if (!allocations.length) {
                target.innerHTML = empty("There are no currently admitted patients.", "No active admissions");
                return;
            }
            target.innerHTML = `<div class="table-wrap"><table class="table"><thead><tr><th>Patient</th><th>Department</th><th>Bed</th><th>Admitted</th><th>Action</th></tr></thead><tbody>${allocations.map((item) => `<tr><td>${escapeHtml(item.patientName)}<small class="text-muted"> · ID ${item.patientId}</small></td><td>${escapeHtml(item.departmentName)}</td><td>${escapeHtml(item.bedNumber)}</td><td>${escapeHtml(item.admitDate)}</td><td><button class="btn btn-secondary" type="button" data-discharge-id="${item.id}" data-patient-name="${escapeHtml(item.patientName)}">Discharge</button></td></tr>`).join("")}</tbody></table></div>`;
            target.querySelectorAll("[data-discharge-id]").forEach((button) => {
                button.addEventListener("click", () => discharge(button));
            });
        } catch (error) {
            target.innerHTML = empty(error.message, "Admissions could not load");
            errorMessage(error);
        }
    }

    async function discharge(button) {
        if (!(await app.confirmDialog(`Discharge ${button.dataset.patientName} and free the bed?`, "Confirm discharge"))) {
            return;
        }
        button.disabled = true;
        try {
            const result = await app.json(`/api/staff/beds/${button.dataset.dischargeId}/discharge`, { method: "PUT" });
            app.showToast(`${result.patientName} discharged. Bed ${result.bedNumber} is available.`);
            await Promise.all([loadActiveBeds(), loadAvailableBedsIfSelected()]);
        } catch (error) {
            errorMessage(error);
            button.disabled = false;
        }
    }

    async function loadAvailableBedsIfSelected() {
        const select = document.getElementById("bed-department");
        if (select && select.value) {
            await loadAvailableBeds();
        }
    }

    async function renderLoad() {
        loading("Loading today's patient load by department…");
        try {
            const load = await app.json(api.load);
            content.innerHTML = `
                ${heading("Today's load", "Patient counts recorded today, grouped by department.")}
                <section class="content-grid" aria-label="Department patient load">
                    ${load.length ? load.map((item) => `<article class="card grid-span-4"><div class="card-header"><h2>${escapeHtml(item.department)}</h2><span class="badge badge-ok">Today</span></div><div class="stat-value">${item.patientCount}</div><p class="stat-label">patients recorded</p></article>`).join("") : `<div class="grid-span-12">${empty("No patient activity has been recorded today.", "No load data")}</div>`}
                </section>`;
        } catch (error) {
            content.innerHTML = `${heading("Today's load", "Patient counts recorded today, grouped by department.")}${empty(error.message, "Load data could not load")}`;
            errorMessage(error);
        }
    }

    async function initialize() {
        const user = await app.requireRole("STAFF");
        if (!user) {
            return;
        }
        window.addEventListener("hashchange", () => render(window.location.hash.slice(1)));
        render(window.location.hash.slice(1));
    }

    document.addEventListener("DOMContentLoaded", initialize);
}());
