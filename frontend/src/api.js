export async function callRiskApi({ getToken, apiUrl = '', fetchImpl = fetch }) {
  const token = await getToken();
  const response = await fetchImpl(`${apiUrl.replace(/\/$/, '')}/api/risk-status`, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) {
    const hint = response.status === 401 ? 'El token no es válido para esta API; revisa emisor, audiencia y vigencia.'
      : response.status === 403 ? 'La API rechazó el acceso.' : 'No se pudo completar la solicitud.';
    throw new Error(`API HTTP ${response.status}. ${hint}`);
  }
  let body;
  try { body = await response.json(); }
  catch { throw new Error('La API respondió sin un JSON válido.'); }
  if (!body || body.status !== 'protected' || typeof body.subject !== 'string' || typeof body.control !== 'string') {
    throw new Error('La respuesta no corresponde al endpoint de riesgos.');
  }
  // Only the three expected, non-token fields are displayed.
  return { status: body.status, subject: body.subject, control: body.control };
}
