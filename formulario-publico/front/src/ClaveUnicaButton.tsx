import icon from './assets/cu-blanco.svg';
import type { MouseEventHandler } from 'react';

export function ClaveUnicaButton({onClick}: {onClick?: MouseEventHandler<HTMLAnchorElement>} = {}) {
  return <a className="btn-cu" href="/oauth2/authorization/claveunica" onClick={onClick} aria-label="Iniciar sesión con ClaveÚnica">
    <img src={icon} alt="" width="24" height="24" aria-hidden="true" />
    <span aria-hidden="true">Iniciar sesión</span>
  </a>;
}
