import { birthDateProblem, documentNumberProblem, isUnderage, nameProblem, normalizeName } from './personalData';

describe('datos personales declarados', () => {
  it('acepta nombres legítimos nacionales e internacionales', () => {
    for (const name of ['María José', 'Ñuñoa', "O'Higgins", 'Pérez-Cotapos', 'De la Fuente', 'Ma. José', 'Jr.', 'Zoë', 'François', 'Łukasz', 'Øyvind',
      'Nguyễn', 'Ng', 'Иван', 'Αλέξανδρος', 'محمد', 'דוד', 'Kaʻiulani', '李', '김', 'さくら', 'D́aniel'])
      expect(nameProblem('primerNombre', name, true), name).toBeNull();
  });
  it('normaliza apóstrofos tipográficos, espacios y composición Unicode', () => {
    expect(normalizeName('  O’Higgins  ')).toBe("O'Higgins");
    expect(normalizeName('Ana   María\tJosé')).toBe('Ana María José');
    expect(normalizeName('José')).toBe('José');
    expect(nameProblem('primerApellido', 'O’Higgins', true)).toBeNull();
  });
  it('rechaza números, símbolos y signos sin letras a sus lados', () => {
    expect(nameProblem('primerNombre', 'Ana2', true)).toBe('No incluyas números.');
    expect(nameProblem('primerNombre', '١٢', false)).toBe('No incluyas números.');
    for (const value of ['Ana@', 'Ana_María', '<script>', 'Ana/María', 'Ana, María'])
      expect(nameProblem('primerNombre', value, true), value).toMatch(/^Usa solo letras/);
    for (const value of ['-Ana', 'Ana-', "'Ana", 'Ana--María', 'Ana - María', '.', '...', 'Ana..'])
      expect(nameProblem('primerNombre', value, true), value).toMatch(/^Los espacios y signos/);
  });
  it('exige más de una inicial solo a quien presenta la comunicación', () => {
    expect(nameProblem('primerNombre', 'J', true)).toBe('Escribe el nombre completo, no solo la inicial.');
    expect(nameProblem('primerApellido', 'P.', true)).toBe('Escribe el apellido completo, no solo la inicial.');
    expect(nameProblem('primerNombre', 'J', false)).toBeNull();
    expect(nameProblem('primerNombre', 'J1', false)).toBe('No incluyas números.');
  });
  it('limita el largo a cien caracteres y deja vacío lo opcional', () => {
    expect(nameProblem('segundoNombre', 'A'.repeat(100), true)).toBeNull();
    expect(nameProblem('segundoNombre', 'A'.repeat(101), true)).toBe('Admite hasta 100 caracteres.');
    expect(nameProblem('segundoNombre', '   ', true)).toBeNull();
  });
  it('valida documentos extranjeros sin imponer un formato de país', () => {
    for (const value of ['AB/123-xyz', 'X1234567', 'P 123.456', 'Ж123']) expect(documentNumberProblem(value), value).toBeNull();
    expect(documentNumberProblem('x')).toMatch(/número completo/);
    expect(documentNumberProblem('1-2')).toMatch(/número completo/);
    expect(documentNumberProblem('AB#123')).toMatch(/^Usa solo letras/);
    expect(documentNumberProblem('A'.repeat(41))).toBe('Admite hasta 40 caracteres.');
  });
  it('acota la fecha de nacimiento como el servidor y advierte minoría de edad sin bloquear', () => {
    const today = new Date('2026-11-07T15:00:00Z');
    expect(birthDateProblem('1990-05-17', today)).toBeNull();
    expect(birthDateProblem('2026-11-08', today)).toMatch(/posterior a hoy/);
    expect(birthDateProblem('1890-01-01', today)).toMatch(/año/);
    expect(birthDateProblem('2001-02-29', today)).toMatch(/válidos/);
    expect(isUnderage('2010-01-01', today)).toBe(true);
    expect(isUnderage('2008-11-07', today)).toBe(false);
    expect(isUnderage('nope', today)).toBe(false);
  });
});
