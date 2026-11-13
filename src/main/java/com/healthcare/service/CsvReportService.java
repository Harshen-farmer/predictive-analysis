package com.healthcare.service;

import com.healthcare.dto.AppointmentReportRow;
import com.healthcare.dto.BedOccupancyReportRow;
import com.healthcare.dto.DailyLoadReportRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

@Service
public class CsvReportService {

    public String dailyLoad(List<DailyLoadReportRow> rows) {
        StringBuilder csv = new StringBuilder("date,departmentId,department,patientCount\n");
        rows.forEach(row -> append(csv, row.date(), row.departmentId(), row.department(), row.patientCount()));
        return csv.toString();
    }

    public String bedOccupancy(List<BedOccupancyReportRow> rows) {
        StringBuilder csv = new StringBuilder("date,departmentId,department,occupied,available,maintenance\n");
        rows.forEach(row -> append(csv, row.date(), row.departmentId(), row.department(),
                row.occupied(), row.available(), row.maintenance()));
        return csv.toString();
    }

    public String appointments(List<AppointmentReportRow> rows) {
        StringBuilder csv = new StringBuilder(
                "id,date,slotTime,tokenNumber,patient,doctor,department,status,type\n");
        rows.forEach(row -> append(csv, row.id(), row.date(), row.slotTime(), row.tokenNumber(),
                row.patient(), row.doctor(), row.department(), row.status(), row.type()));
        return csv.toString();
    }

    private void append(StringBuilder csv, Object... values) {
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                csv.append(',');
            }
            csv.append(escape(values[index] == null ? "" : values[index].toString()));
        }
        csv.append('\n');
    }

    private String escape(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
