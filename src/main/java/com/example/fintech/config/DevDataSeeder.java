package com.example.fintech.config;

import com.example.fintech.cheque.Cheque;
import com.example.fintech.cheque.ChequeService;
import com.example.fintech.cliente.Cliente;
import com.example.fintech.cliente.ClienteService;
import com.example.fintech.cliente.TipoPersona;
import com.example.fintech.cuentacorriente.CuentaCorriente;
import com.example.fintech.cuentacorriente.CuentaCorrienteService;
import com.example.fintech.librador.Librador;
import com.example.fintech.librador.LibradorService;
import com.example.fintech.prestamo.CuotaPrestamo;
import com.example.fintech.prestamo.CuotaPrestamoService;
import com.example.fintech.prestamo.Prestamo;
import com.example.fintech.prestamo.PrestamoService;
import com.example.fintech.prestamo.SistemaAmortizacion;
import com.example.fintech.solicitud.EstadoSolicitud;
import com.example.fintech.solicitud.SolicitudCredito;
import com.example.fintech.solicitud.SolicitudCreditoService;
import com.example.fintech.solicitud.TipoSolicitud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Carga datos de ejemplo la primera vez que la app arranca contra una base
 * vacía (se salta si ya hay clientes). Pasa por los services, no por SQL
 * directo, para que los préstamos/cuotas seedeados generen asientos
 * contables balanceados y eventos outbox reales, igual que datos cargados
 * por un usuario.
 */
@Component
public class DevDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

	private final ClienteService clienteService;
	private final LibradorService libradorService;
	private final SolicitudCreditoService solicitudCreditoService;
	private final PrestamoService prestamoService;
	private final CuotaPrestamoService cuotaPrestamoService;
	private final ChequeService chequeService;
	private final CuentaCorrienteService cuentaCorrienteService;

	public DevDataSeeder(ClienteService clienteService, LibradorService libradorService,
			SolicitudCreditoService solicitudCreditoService, PrestamoService prestamoService,
			CuotaPrestamoService cuotaPrestamoService, ChequeService chequeService,
			CuentaCorrienteService cuentaCorrienteService) {
		this.clienteService = clienteService;
		this.libradorService = libradorService;
		this.solicitudCreditoService = solicitudCreditoService;
		this.prestamoService = prestamoService;
		this.cuotaPrestamoService = cuotaPrestamoService;
		this.chequeService = chequeService;
		this.cuentaCorrienteService = cuentaCorrienteService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!clienteService.findAll().isEmpty()) {
			log.info("ya hay clientes cargados, se omite el seed de datos de ejemplo");
			return;
		}
		log.info("base sin clientes, cargando datos de ejemplo...");

		Cliente comercial = crearCliente("20304050617", "Comercial San Martín SRL", TipoPersona.JURIDICA,
				"contacto@comercialsanmartin.com.ar");
		Cliente fernandez = crearCliente("27111222338", "María Fernanda López", TipoPersona.FISICA,
				"mflopez@example.com");
		Cliente transportes = crearCliente("30555666677", "Transportes Andina SA", TipoPersona.JURIDICA,
				"administracion@transportesandina.com.ar");

		Librador libradorUno = crearLibrador("30111222339", "Distribuidora del Sur SA");
		Librador libradorDos = crearLibrador("30222333344", "Insumos Industriales Cuyo SRL");

		SolicitudCredito solicitudAprobada = crearSolicitud(comercial, TipoSolicitud.PRESTAMO,
				new BigDecimal("200000.00"), EstadoSolicitud.APROBADA);
		crearSolicitud(transportes, TipoSolicitud.DESCUENTO_CHEQUE, new BigDecimal("80000.00"),
				EstadoSolicitud.EN_EVALUACION);

		Prestamo prestamoComercial = crearPrestamo(comercial, solicitudAprobada, new BigDecimal("200000.00"),
				new BigDecimal("42.5"), SistemaAmortizacion.FRANCES, 12);
		Prestamo prestamoFernandez = crearPrestamo(fernandez, null, new BigDecimal("50000.00"),
				new BigDecimal("38.0"), SistemaAmortizacion.ALEMAN, 6);

		CuotaPrestamo cuota1 = crearCuota(prestamoComercial, 1, new BigDecimal("19500.00"),
				LocalDate.now().plusMonths(1));
		crearCuota(prestamoComercial, 2, new BigDecimal("19500.00"), LocalDate.now().plusMonths(2));
		crearCuota(prestamoComercial, 3, new BigDecimal("19500.00"), LocalDate.now().plusMonths(3));
		crearCuota(prestamoFernandez, 1, new BigDecimal("9200.00"), LocalDate.now().plusMonths(1));
		crearCuota(prestamoFernandez, 2, new BigDecimal("9200.00"), LocalDate.now().plusMonths(2));

		cuentaCorrienteService.create(cuentaCorriente(comercial));
		cuentaCorrienteService.create(cuentaCorriente(fernandez));
		cuentaCorrienteService.create(cuentaCorriente(transportes));

		cuotaPrestamoService.registrarPago(cuota1.getId(), new BigDecimal("19500.00"), "seed-pago-cuota-1");

		crearCheque(transportes, libradorUno, "00045678", "Banco Nación", new BigDecimal("80000.00"),
				LocalDate.now().minusDays(10), LocalDate.now().plusDays(20));
		crearCheque(comercial, libradorDos, "00098123", "Banco Galicia", new BigDecimal("35000.00"),
				LocalDate.now().minusDays(5), LocalDate.now().plusDays(45));

		log.info("datos de ejemplo cargados: 3 clientes, 2 libradores, 2 solicitudes, 2 préstamos, "
				+ "5 cuotas (1 pagada), 2 cuentas corrientes, 2 cheques");
	}

	private Cliente crearCliente(String cuit, String razonSocial, TipoPersona tipo, String email) {
		Cliente cliente = new Cliente();
		cliente.setCuit(cuit);
		cliente.setRazonSocial(razonSocial);
		cliente.setTipoPersona(tipo);
		cliente.setEmail(email);
		return clienteService.create(cliente);
	}

	private Librador crearLibrador(String cuit, String razonSocial) {
		Librador librador = new Librador();
		librador.setCuit(cuit);
		librador.setRazonSocial(razonSocial);
		return libradorService.create(librador);
	}

	private SolicitudCredito crearSolicitud(Cliente cliente, TipoSolicitud tipo, BigDecimal monto,
			EstadoSolicitud estadoFinal) {
		SolicitudCredito solicitud = new SolicitudCredito();
		solicitud.setCliente(cliente);
		solicitud.setTipo(tipo);
		solicitud.setMontoSolicitado(monto);
		SolicitudCredito creada = solicitudCreditoService.create(solicitud);
		if (estadoFinal != EstadoSolicitud.INICIADA) {
			return solicitudCreditoService.actualizarEstado(creada.getId(), estadoFinal);
		}
		return creada;
	}

	private Prestamo crearPrestamo(Cliente cliente, SolicitudCredito solicitud, BigDecimal monto,
			BigDecimal tasaAnual, SistemaAmortizacion sistema, int plazoMeses) {
		Prestamo prestamo = new Prestamo();
		prestamo.setCliente(cliente);
		prestamo.setSolicitud(solicitud);
		prestamo.setMonto(monto);
		prestamo.setTasaAnual(tasaAnual);
		prestamo.setSistemaAmortizacion(sistema);
		prestamo.setPlazoMeses(plazoMeses);
		return prestamoService.create(prestamo);
	}

	private CuotaPrestamo crearCuota(Prestamo prestamo, int numero, BigDecimal monto, LocalDate fechaVencimiento) {
		CuotaPrestamo cuota = new CuotaPrestamo();
		cuota.setPrestamo(prestamo);
		cuota.setNumero(numero);
		cuota.setMonto(monto);
		cuota.setFechaVencimiento(fechaVencimiento);
		return cuotaPrestamoService.create(cuota);
	}

	private CuentaCorriente cuentaCorriente(Cliente cliente) {
		CuentaCorriente cuenta = new CuentaCorriente();
		cuenta.setCliente(cliente);
		cuenta.setMoneda("ARS");
		return cuenta;
	}

	private Cheque crearCheque(Cliente cliente, Librador librador, String numero, String banco, BigDecimal monto,
			LocalDate fechaEmision, LocalDate fechaVencimiento) {
		Cheque cheque = new Cheque();
		cheque.setCliente(cliente);
		cheque.setLibrador(librador);
		cheque.setNumero(numero);
		cheque.setBanco(banco);
		cheque.setMonto(monto);
		cheque.setFechaEmision(fechaEmision);
		cheque.setFechaVencimiento(fechaVencimiento);
		return chequeService.create(cheque);
	}
}
