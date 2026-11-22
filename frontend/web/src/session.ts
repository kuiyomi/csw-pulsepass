const clientId = "pulsepass-spa";
const realm = "http://localhost:8081/realms/pulsepass/protocol/openid-connect";
const redirect = window.location.origin + "/";
type Tokens = {access_token: string; refresh_token: string; id_token?: string};
type Claims = {sub: string; preferred_username: string; exp: number; realm_access?: {roles: string[]}; nonce?: string};
let tokens: Tokens | null = null;
let refresh: Promise<string> | undefined;
const listeners = new Set<() => void>();
export function observeSession(fn: () => void) {
    listeners.add(fn);
    return () => { listeners.delete(fn); };
}
function remember(t: Tokens | null) {
    tokens = t;
    if (t) sessionStorage.setItem("pulsepass.session", JSON.stringify(t));
    else sessionStorage.removeItem("pulsepass.session");
    listeners.forEach(fn => fn());
}
function claims(raw: string): Claims {
    const bytes = Uint8Array.from(atob(raw.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")), c => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
}
export function account() { return tokens ? claims(tokens.access_token) : null; }
const base64url = (data: Uint8Array) => btoa(String.fromCharCode(...data)).replace(/\+/g,"-").replace(/\//g,"_").replace(/=+$/,"");
async function exchange(form: Record<string,string>): Promise<Tokens> {
    const r = await fetch(realm + "/token", {method:"POST", body:new URLSearchParams({client_id:clientId,...form})});
    if (!r.ok) throw new Error("A sessão expirou. Entre novamente.");
    return r.json();
}
export async function login(register = false) {
    const verifier = base64url(crypto.getRandomValues(new Uint8Array(48)));
    const challenge = base64url(new Uint8Array(await crypto.subtle.digest("SHA-256",new TextEncoder().encode(verifier))));
    const state = crypto.randomUUID(), nonce = crypto.randomUUID();
    sessionStorage.setItem("pulsepass.auth", JSON.stringify({state,nonce,verifier,route:location.hash}));
    location.assign(realm + (register ? "/registrations?" : "/auth?") + new URLSearchParams({
        client_id:clientId, redirect_uri:redirect, response_type:"code",scope:"openid profile",state,nonce,
        code_challenge:challenge,code_challenge_method:"S256"
    }));
}
export async function initialize() {
    const query = new URLSearchParams(location.search);
    if (query.has("code") || query.has("error")) {
        const pending = JSON.parse(sessionStorage.getItem("pulsepass.auth") || "null");
        sessionStorage.removeItem("pulsepass.auth");
        history.replaceState(null,"",redirect);
        if (!pending || pending.state !== query.get("state") || !query.get("code")) throw new Error("A entrada não foi concluída.");
        const result = await exchange({grant_type:"authorization_code",code:query.get("code")!,redirect_uri:redirect,code_verifier:pending.verifier});
        if (!result.id_token || claims(result.id_token).nonce !== pending.nonce) throw new Error("Resposta de login inválida.");
        remember(result); location.hash = pending.route;
    } else {
        const saved = sessionStorage.getItem("pulsepass.session");
        if (saved) { tokens = JSON.parse(saved); await accessToken(); }
    }
}
export async function accessToken(force = false): Promise<string> {
    if (!tokens) throw new Error("Entre na sua conta.");
    if (!force && claims(tokens.access_token).exp > Date.now()/1000 + 30) return tokens.access_token;
    if (!refresh) refresh = exchange({grant_type:"refresh_token",refresh_token:tokens.refresh_token})
        .then(t => {remember(t);return t.access_token;}).catch(e => {remember(null);throw e;}).finally(() => {refresh=undefined;});
    return refresh;
}
export async function logout() {
    if (tokens) {
        const token = await accessToken();
        const r = await fetch("/api/session/logout",{method:"POST",headers:{Authorization:"Bearer "+token}});
        if (!r.ok) throw new Error("Falha ao encerrar a sessão. Tente novamente.");
        const response=await fetch(realm+"/logout",{method:"POST",body:new URLSearchParams({client_id:clientId,refresh_token:tokens.refresh_token})});
        if (!response.ok) throw new Error("Falha ao encerrar a sessão no Keycloak.");
    }
    remember(null);location.hash="";
}
export class ApiError extends Error {
    constructor(public status:number, detail:string, public retryable=false) {super(detail);}
}
export async function api<T>(path:string,method="GET",body?:unknown,key?:string):Promise<T> {
    const headers:Record<string,string>={Authorization:"Bearer "+await accessToken()};
    if (body!==undefined) headers["Content-Type"]="application/json";
    if(key) headers["Idempotency-Key"]=key;
    const response=await fetch(path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
    if(!response.ok) {
        const info=await response.json().catch(()=>({}));
        throw new ApiError(response.status,info.detail || info.title || "Falha na operação ("+response.status+").",response.headers.has("Retry-After"));
    }
    return response.status===204 ? undefined as T : response.json();
}
