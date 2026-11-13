(function () {
    "use strict";

    const form = document.getElementById("login-form");
    if (!form) {
        return;
    }

    const message = document.getElementById("message");
    const submitButton = document.getElementById("submit-button");
    const buttonLabel = document.getElementById("button-label");
    const spinner = document.getElementById("loading-spinner");
    const inputs = ["username", "password"].map((id) => ({
        input: document.getElementById(id),
        error: document.getElementById(id + "-error")
    }));

    function validateField(field) {
        const value = field.input.value.trim();
        const text = value ? "" : field.input.labels[0].textContent + " is required.";
        field.error.textContent = text;
        field.input.setAttribute("aria-invalid", text ? "true" : "false");
        return !text;
    }

    inputs.forEach((field) => {
        field.input.addEventListener("blur", () => validateField(field));
        field.input.addEventListener("input", () => {
            if (field.input.getAttribute("aria-invalid") === "true") {
                validateField(field);
            }
            message.textContent = "";
        });
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        message.textContent = "";
        const valid = inputs.map(validateField).every(Boolean);
        if (!valid) {
            inputs.find((field) => field.input.getAttribute("aria-invalid") === "true").input.focus();
            return;
        }

        submitButton.disabled = true;
        spinner.hidden = false;
        buttonLabel.textContent = "Signing in…";

        try {
            const response = await HealthcareApp.request(window.location.pathname, {
                method: "POST",
                headers: { "Content-Type": "application/x-www-form-urlencoded" },
                body: new URLSearchParams(new FormData(form))
            });
            const result = await response.json();
            if (!response.ok) {
                message.textContent = result.error || "Invalid credentials";
                return;
            }
            window.location.assign(result.redirect);
        } catch (error) {
            message.textContent = error.message || "Unable to sign in. Please try again.";
        } finally {
            submitButton.disabled = false;
            spinner.hidden = true;
            buttonLabel.textContent = "Sign in";
        }
    });
}());
