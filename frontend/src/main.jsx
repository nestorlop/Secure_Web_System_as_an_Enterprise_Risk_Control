import React from 'react';
import { createRoot } from 'react-dom/client';
import { Auth0Provider } from '@auth0/auth0-react';
import App from './App.jsx';
import './style.css';

const domain = import.meta.env.VITE_AUTH0_DOMAIN;
const clientId = import.meta.env.VITE_AUTH0_CLIENT_ID;
const audience = import.meta.env.VITE_AUTH0_AUDIENCE;
const configured = domain && clientId && audience && !/YOUR_|[/:]/.test(domain) && !clientId.startsWith('YOUR_');

createRoot(document.getElementById('root')).render(configured ? (
  <Auth0Provider domain={domain} clientId={clientId} cacheLocation="memory"
    authorizationParams={{ redirect_uri: window.location.origin, audience, scope: 'openid profile email read:risk' }}
    onRedirectCallback={() => window.history.replaceState({}, document.title, window.location.pathname)}>
    <App />
  </Auth0Provider>
) : <main><p className="eyebrow">ENTERPRISE RISK LAB</p><h1>Configura Auth0 para comenzar</h1>
  <p>Copia <code>.env.example</code> a <code>.env.local</code>, completa el dominio y client ID públicos y reinicia Vite.</p>
  <p>La audiencia debe ser <code>https://enterprise-risk-api</code>. Consulta el README.</p></main>);
