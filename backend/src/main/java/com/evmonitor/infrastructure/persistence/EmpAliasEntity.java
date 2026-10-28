package com.evmonitor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Anderer Name einer Ladekarte für einen Anbieter im Katalog ("enbw mobility+" für EnBW), V220. Alias klein geschrieben. */
@Entity
@Table(name = "emp_alias")
@Getter
@Setter
@NoArgsConstructor
public class EmpAliasEntity {

    @Id
    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "emp_name", nullable = false, length = 100)
    private String empName;
}
