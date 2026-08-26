package com.example.fintech.scoring;

import com.example.fintech.cliente.Cliente;
import com.example.fintech.encryption.EncryptedJsonMapConverter;
import com.example.fintech.solicitud.SolicitudCredito;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Se crea únicamente vía ScoringSagaService al procesar la respuesta de un
 * proveedor externo, nunca directamente.
 */
@Getter
@Setter
@Entity
@Table(name = "resultados_scoring")
public class ResultadoScoring {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "solicitud_id")
	private SolicitudCredito solicitud;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "cliente_id", nullable = false)
	private Cliente cliente;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.NAMED_ENUM)
	@Column(nullable = false)
	private ProveedorScoring proveedor;

	private Integer score;

	@Convert(converter = EncryptedJsonMapConverter.class)
	@Column(name = "respuesta_raw", nullable = false)
	private Map<String, Object> respuestaRaw = Map.of();

	@Column(name = "fecha_consulta", nullable = false)
	private OffsetDateTime fechaConsulta;

	@PrePersist
	void prePersist() {
		fechaConsulta = OffsetDateTime.now();
	}
}
