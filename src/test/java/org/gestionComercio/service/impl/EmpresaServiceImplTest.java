package org.gestionComercio.service.impl;

import org.gestionComercio.dto.empresa.EmpresaDto;
import org.gestionComercio.entity.Empresa;
import org.gestionComercio.entity.Usuario;
import org.gestionComercio.enums.CondicionIVA;
import org.gestionComercio.enums.PermisoCodigo;
import org.gestionComercio.exception.AuthorizationException;
import org.gestionComercio.exception.EntityNotFoundException;
import org.gestionComercio.exception.ValidationException;
import org.gestionComercio.mapper.EmpresaMapper;
import org.gestionComercio.repository.EmpresaRepository;
import org.gestionComercio.security.SesionUsuario;
import org.gestionComercio.validation.EmpresaValidator;
import org.gestionComercio.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmpresaServiceImplTest {

    @Mock
    private EmpresaRepository empresaRepository;

    @Mock
    private EmpresaMapper empresaMapper;

    @Mock
    private EmpresaValidator empresaValidator;

    private SesionUsuario sesionUsuario;

    private EmpresaServiceImpl empresaService;

    @BeforeEach
    void setUp() {
        sesionUsuario = new SesionUsuario();
        loguearCon(EnumSet.allOf(PermisoCodigo.class));

        empresaService = new EmpresaServiceImpl(
                empresaRepository, empresaMapper, empresaValidator, sesionUsuario);

        lenient().when(empresaValidator.validate(any())).thenReturn(new ValidationResult());
    }

    private void loguearCon(EnumSet<PermisoCodigo> permisos) {
        sesionUsuario.iniciarSesion(new Usuario(), permisos);
    }

    private Empresa empresa() {
        Empresa empresa = Empresa.builder()
                .razonSocial("Empresa Demo")
                .cuit("30000000007")
                .condicionIva(CondicionIVA.RESPONSABLE_INSCRIPTO)
                .build();
        empresa.setId(1L);
        return empresa;
    }

    // ------------------------------------------------------------------
    // Consulta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("obtener() devuelve la única empresa")
    void obtenerDevuelveEmpresa() {
        Empresa empresa = empresa();
        EmpresaDto dto = EmpresaDto.builder().id(1L).razonSocial("Empresa Demo").build();

        when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(empresa));
        when(empresaMapper.toDto(empresa)).thenReturn(dto);

        assertSame(dto, empresaService.obtener());
    }

    @Test
    @DisplayName("obtener() falla si no hay empresa cargada")
    void obtenerSinEmpresa() {
        when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> empresaService.obtener());
    }

    @Test
    @DisplayName("obtener() requiere EMPRESA_VER")
    void obtenerSinPermiso() {
        EnumSet<PermisoCodigo> permisos = EnumSet.allOf(PermisoCodigo.class);
        permisos.remove(PermisoCodigo.EMPRESA_VER);
        loguearCon(permisos);

        assertThrows(AuthorizationException.class, () -> empresaService.obtener());
        verifyNoInteractions(empresaRepository);
    }

    // ------------------------------------------------------------------
    // Edición
    // ------------------------------------------------------------------

    @Test
    @DisplayName("actualizar() normaliza los datos y edita el único registro, sin importar el ID recibido")
    void actualizarNormalizaYGuarda() {
        Empresa empresa = empresa();

        EmpresaDto dto = EmpresaDto.builder()
                .id(99L)
                .razonSocial("  Comercio SRL  ")
                .nombreFantasia("   ")
                .cuit(" 30-71234567-1 ")
                .ingresosBrutos(" 901-123456-7 ")
                .condicionIva(CondicionIVA.MONOTRIBUTISTA)
                .direccion("")
                .telefono(" 351 1234567 ")
                .email(" ventas@comercio.com ")
                .logo("  ")
                .build();

        when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(empresa));
        when(empresaRepository.save(empresa)).thenReturn(empresa);

        empresaService.actualizar(dto);

        assertEquals(1L, dto.getId());
        assertEquals("Comercio SRL", dto.getRazonSocial());
        assertNull(dto.getNombreFantasia());
        assertEquals("30712345671", dto.getCuit());
        assertEquals("901-123456-7", dto.getIngresosBrutos());
        assertNull(dto.getDireccion());
        assertEquals("351 1234567", dto.getTelefono());
        assertEquals("ventas@comercio.com", dto.getEmail());
        assertNull(dto.getLogo());

        verify(empresaValidator).validate(dto);
        verify(empresaMapper).updateEntity(dto, empresa);
        verify(empresaRepository).save(empresa);
    }

    @Test
    @DisplayName("actualizar() no guarda si la validación falla")
    void actualizarConErrores() {
        ValidationResult conErrores = new ValidationResult();
        conErrores.addError("cuit", "El CUIT es obligatorio.");

        when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.of(empresa()));
        when(empresaValidator.validate(any())).thenReturn(conErrores);

        EmpresaDto dto = EmpresaDto.builder().razonSocial("Comercio SRL").build();

        assertThrows(ValidationException.class, () -> empresaService.actualizar(dto));
        verify(empresaMapper, never()).updateEntity(any(), any());
        verify(empresaRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar() falla si no hay empresa cargada")
    void actualizarSinEmpresa() {
        when(empresaRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        EmpresaDto dto = EmpresaDto.builder().razonSocial("Comercio SRL").build();

        assertThrows(EntityNotFoundException.class, () -> empresaService.actualizar(dto));
        verify(empresaRepository, never()).save(any());
    }

    @Test
    @DisplayName("actualizar() requiere EMPRESA_EDITAR (no alcanza con EMPRESA_VER)")
    void actualizarSinPermiso() {
        loguearCon(EnumSet.of(PermisoCodigo.EMPRESA_VER));

        EmpresaDto dto = EmpresaDto.builder().razonSocial("Comercio SRL").build();

        assertThrows(AuthorizationException.class, () -> empresaService.actualizar(dto));
        verifyNoInteractions(empresaRepository, empresaValidator, empresaMapper);
    }
}
