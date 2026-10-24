import logo from './assets/logo.svg';
export function InstitutionalHeader({theme, scale, onTheme, onIncrease, onDecrease, title = 'Formulario de contacto ciudadano', onHome}: {theme: string; scale: number; onTheme: (v: string) => void; onIncrease: () => void; onDecrease: () => void; title?: string; onHome?: () => void}) { return <><svg
      aria-hidden="true"
      focusable="false"
      className="icon-sprite"
      xmlns="http://www.w3.org/2000/svg"
    >
      <symbol id="i-message" viewBox="0 0 24 24">
        <path
          d="M20 11.5a7.9 7.9 0 0 1-8.2 8 10 10 0 0 1-3.7-.7L4 20l1.2-3.8a8 8 0 1 1 14.8-4.7Z"
        ></path>
        <path d="M8.5 10.7h7M8.5 14h4.5"></path>
      </symbol>
      <symbol id="i-info" viewBox="0 0 24 24">
        <circle cx="12" cy="12" r="9"></circle>
        <path d="M12 11v5M12 8h.01"></path>
      </symbol>
      <symbol id="i-lock" viewBox="0 0 24 24">
        <rect height="11" rx="2" width="14" x="5" y="10"></rect>
        <path d="M8 10V7a4 4 0 0 1 8 0v3"></path>
      </symbol>
      <symbol id="i-check" viewBox="0 0 24 24">
        <path d="m4.5 12 5 5 10-10"></path>
      </symbol>
      <symbol id="i-arrow" viewBox="0 0 24 24">
        <path d="M4 12h15m-6-6 6 6-6 6"></path>
      </symbol>
      <symbol id="i-sliders" viewBox="0 0 24 24">
        <path d="M4 7h16M4 17h16M9 4v6M15 14v6"></path>
      </symbol>
      <symbol id="i-paper" viewBox="0 0 24 24">
        <path
          d="M8 3h7l5 5v12a1 1 0 0 1-1 1H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h1Z"
        ></path>
        <path d="M14 3v6h6M9 13h7M9 17h6"></path>
      </symbol>
      <symbol id="i-upload" viewBox="0 0 24 24">
        <path d="M12 16V4m-5 5 5-5 5 5M5 16v4h14v-4"></path>
      </symbol>
      <symbol id="i-alert" viewBox="0 0 24 24">
        <path
          d="M10.5 3.5a2 2 0 0 1 3 0l8 14a2 2 0 0 1-1.7 3H4.2a2 2 0 0 1-1.7-3l8-14Z"
        ></path>
        <path d="M12 9v5M12 17h.01"></path>
      </symbol>
      <symbol id="i-chevron" viewBox="0 0 24 24">
        <path d="m6 9 6 6 6-6"></path>
      </symbol>
      <symbol id="i-x" viewBox="0 0 24 24">
        <path d="M5 5 19 19M19 5 5 19"></path>
      </symbol>
      <symbol id="i-heart" viewBox="0 0 24 24">
        <path
          d="M20.4 7.2a4.7 4.7 0 0 0-6.7 0L12 8.9l-1.7-1.7a4.7 4.7 0 0 0-6.7 6.6L12 22l8.4-8.2a4.7 4.7 0 0 0 0-6.6Z"
        ></path>
      </symbol>
    </svg><div aria-hidden="true" className="brand-stripe">
      <span></span><span></span><span></span><span></span><span></span
      ><span></span><span></span><span></span>
    </div><header className="site-header">
      <div className="shell header-inner">
        <div className="header-brand">
          <div className="logo-plate">
            <img
              alt="Defensoría de la Niñez"
              className="logo"
              height="98"
              src={logo}
              width="224"
            />
          </div>
          <div aria-hidden="true" className="header-divider"></div>
          <div className="service-label">
            <small>Servicios a la ciudadanía</small
            ><strong id="header-form-title"
              >{title}</strong
            >
          </div>
        </div>
        <div className="header-actions">
          <button
            aria-label="Volver al inicio"
            className="home-nav"
            hidden={!onHome} onClick={onHome}
            id="go-home"
            type="button"
          >
            <svg aria-hidden="true" className="icon small">
              <use href="#i-arrow"></use>
            </svg>
            Inicio
          </button>
          <div
            aria-label="Opciones de accesibilidad"
            className="accessibility-bar"
            role="group"
          >
            <label className="sr-only" htmlFor="theme-choice"
              >Apariencia de la pantalla</label
            >
            <select
              aria-label="Apariencia: claro, oscuro o alto contraste"
              id="theme-choice" value={theme} onChange={e => onTheme(e.target.value)}
            >
              <option value="light">Modo claro</option>
              <option value="dark">Modo oscuro</option>
              <option value="contrast">Alto contraste</option>
            </select>
            <div
              aria-label="Tamaño del texto"
              className="font-controls"
              role="group"
            >
              <button
                aria-label="Reducir tamaño de texto"
                id="font-decrease" onClick={onDecrease} disabled={scale <= 100}
                title="Reducir texto"
                type="button"
              >
                A-</button
              ><output
                aria-label="Escala del texto"
                aria-live="polite"
                id="font-status"
                >{scale}%</output
              ><button
                aria-label="Aumentar tamaño de texto"
                id="font-increase" onClick={onIncrease} disabled={scale >= 130}
                title="Aumentar texto"
                type="button"
              >
                A+
              </button>
            </div>
          </div>
        </div>
      </div>
    </header></>; }
