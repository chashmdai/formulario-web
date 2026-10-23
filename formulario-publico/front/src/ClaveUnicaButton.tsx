import icon from './assets/cu-blanco.svg';

export function ClaveUnicaButton() {
  return <a className="btn-cu" href="/oauth2/authorization/claveunica" aria-label="Iniciar sesión con ClaveÚnica">
    <img src={icon} alt="" width="24" height="24" aria-hidden="true" />
    <span aria-hidden="true">Iniciar sesión</span>
  </a>;
}
