const PAYPAL_SANDBOX = "https://api-m.sandbox.paypal.com";
const PAYPAL_LIVE = "https://api-m.paypal.com";

function env(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Missing environment variable: ${name}`);
  return v;
}

function paypalBase() {
  return String(process.env.PAYPAL_ENV || "sandbox").toLowerCase() === "live"
    ? PAYPAL_LIVE : PAYPAL_SANDBOX;
}

async function accessToken() {
  const id = env("PAYPAL_CLIENT_ID");
  const secret = env("PAYPAL_CLIENT_SECRET");
  const auth = Buffer.from(`${id}:${secret}`).toString("base64");
  const r = await fetch(`${paypalBase()}/v1/oauth2/token`, {
    method: "POST",
    headers: {
      "Authorization": `Basic ${auth}`,
      "Content-Type": "application/x-www-form-urlencoded"
    },
    body: "grant_type=client_credentials"
  });
  if (!r.ok) throw new Error(`PayPal OAuth failed: ${r.status}`);
  return (await r.json()).access_token;
}

async function verifyPayPal(rawEvent, headers) {
  const token = await accessToken();
  const event = JSON.parse(rawEvent);
  const payload = {
    auth_algo: headers.get("paypal-auth-algo"),
    cert_url: headers.get("paypal-cert-url"),
    transmission_id: headers.get("paypal-transmission-id"),
    transmission_sig: headers.get("paypal-transmission-sig"),
    transmission_time: headers.get("paypal-transmission-time"),
    webhook_id: env("PAYPAL_WEBHOOK_ID"),
    webhook_event: event
  };
  const r = await fetch(`${paypalBase()}/v1/notifications/verify-webhook-signature`, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });
  if (!r.ok) throw new Error(`PayPal verify failed: ${r.status}`);
  const j = await r.json();
  return { verified: j.verification_status === "SUCCESS", event };
}

async function forwardToAppsScript(event) {
  const gsUrl = env("LAZYTV_GS_URL");
  const secret = env("LAZYTV_INTERNAL_SECRET");
  const sep = gsUrl.includes("?") ? "&" : "?";
  const r = await fetch(`${gsUrl}${sep}action=paypalverified`, {
    method: "POST",
    headers: {"Content-Type":"application/json"},
    body: JSON.stringify({action:"paypalverified", internal_secret:secret, event})
  });
  const txt = await r.text();
  if (!r.ok) throw new Error(`Apps Script forward failed: ${r.status} ${txt.slice(0,200)}`);
  return txt;
}

exports.handler = async (event) => {
  if (event.httpMethod !== "POST") {
    return {statusCode:405, body:JSON.stringify({ok:false,error:"METHOD_NOT_ALLOWED"})};
  }
  try {
    const headers = new Headers(event.headers || {});
    const raw = event.body || "";
    const {verified, event:paypalEvent} = await verifyPayPal(raw, headers);
    if (!verified) {
      return {statusCode:400, body:JSON.stringify({ok:false,error:"INVALID_PAYPAL_SIGNATURE"})};
    }
    await forwardToAppsScript(paypalEvent);
    return {statusCode:200, body:JSON.stringify({ok:true,verified:true,event_id:paypalEvent.id,event_type:paypalEvent.event_type})};
  } catch (e) {
    return {statusCode:500, body:JSON.stringify({ok:false,error:String(e.message || e)})};
  }
};