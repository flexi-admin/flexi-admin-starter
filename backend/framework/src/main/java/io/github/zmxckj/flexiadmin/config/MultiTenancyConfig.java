package io.github.zmxckj.flexiadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "flexi.multi-tenancy")
public class MultiTenancyConfig {
    
    private boolean enabled = true;
    
    private String tenantIdColumn = "tenant_id";
    
    private List<String> ignoreTables = new ArrayList<>();

    private static final List<String> DEFAULT_IGNORE_TABLES = Arrays.asList(
            "sys_tenant",
            "sys_config",
            "sys_role",
            "sys_menu",
            "sys_dict",
            "sys_user",
            "sys_user_role",
            "sys_role_menu",
            "sys_user_dept"
    );

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantIdColumn() {
        return tenantIdColumn;
    }

    public void setTenantIdColumn(String tenantIdColumn) {
        this.tenantIdColumn = tenantIdColumn;
    }

    public List<String> getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(List<String> ignoreTables) {
        if (ignoreTables == null || ignoreTables.isEmpty()) {
            this.ignoreTables = new ArrayList<>(DEFAULT_IGNORE_TABLES);
        } else {
            Set<String> mergedSet = new HashSet<>(DEFAULT_IGNORE_TABLES);
            mergedSet.addAll(ignoreTables);
            this.ignoreTables = new ArrayList<>(mergedSet);
        }
    }
}