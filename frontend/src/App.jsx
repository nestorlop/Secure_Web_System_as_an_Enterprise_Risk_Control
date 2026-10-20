import React, { useState } from 'react';
import { useAuth0 } from '@auth0/auth0-react';
import { callRiskApi } from './api.js';

export default function App() {
  const { isLoading, isAuthenticated, user, error, loginWithRedirect, logout, getAccessTokenSilently } = useAuth0();
  const [result, setResult] = useState(null);
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  async function callApi() {
    setBusy(true); setMessage(''); setResult(null);
    try { setResult(await callRiskApi({ getToken: getAccessTokenSilently, apiUrl: import.meta.env.VITE_API_URL ?? '' })); }
    catch (ex) {
      // Avoid rendering raw Auth0 errors that may include sensitive request context.
      setMessage(ex.message?.startsWith('API HTTP') || ex.message?.startsWith('La ')
        ? ex.message : 'No se pudo obtener el token o contactar la API. Revisa la conexión, CORS y el acceso delegado de Auth0.');
    } finally { setBusy(false); }
  }
  return <main>
    <p className="eyebrow">ENTERPRISE RISK LAB</p>
    <h1>Acceso seguro.<br/>Riesgo controlado.</h1>
    <p>Autenticación con Auth0 · API Spring Boot · JWT RS256</p>
    {isLoading ? <p role="status">Comprobando sesión…</p> : <>
      {error && <p role="alert">{error.message?.includes('not authorized to access resource server')
        ? 'La SPA no tiene acceso delegado a la API. En Auth0 abre esta aplicación → APIs → Enterprise Risk Lab API → Edit → User-Delegated Access, concede read:risk y guarda.'
        : 'Auth0 no pudo completar el inicio de sesión. Revisa las URL autorizadas, la conexión de usuarios y el acceso delegado a la API.'}</p>}
      {!isAuthenticated ? <button onClick={() => loginWithRedirect().catch(() => setMessage('No se pudo abrir Auth0. Revisa la configuración.'))}>Iniciar sesión</button> : <>
        <p className="session">Sesión de <strong>{user?.name ?? user?.email ?? 'usuario autenticado'}</strong></p>
        <div className="actions"><button disabled={busy} onClick={callApi}>{busy ? 'Consultando…' : 'Consultar API protegida'}</button>
          <button className="secondary" disabled={busy} onClick={() => logout({ logoutParams: { returnTo: window.location.origin } }).catch(() => setMessage('No se pudo cerrar sesión. Revisa Allowed Logout URLs.'))}>Cerrar sesión</button></div>
      </>}
      <section aria-live="polite" aria-busy={busy}><h2>Resultado de /api/risk-status</h2>
        {message && <p role="alert">{message}</p>}
        {result ? <><p className="success">HTTP 200 · Acceso autenticado</p><pre>{JSON.stringify(result, null, 2)}</pre></> : <p>Inicia sesión y consulta la API para verificar el control.</p>}
      </section>
    </>}
    <footer>Los tokens se mantienen en memoria y no se muestran en esta página.</footer>
  </main>;
}
