"""Teste HTTP de compra no Compose local. Cria um evento exclusivo por execução."""
import base64
import hashlib
import json
import os
import secrets
import uuid
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen

WEB = "http://localhost:5173"
OIDC = "http://localhost:8081/realms/pulsepass/protocol/openid-connect"


class LoginForm(HTMLParser):
    action = None

    def handle_starttag(self, tag, attrs):
        fields = dict(attrs)
        if tag == "form" and fields.get("id") == "kc-form-login":
            self.action = fields["action"]


def login(user):
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip("=")
    client = {"client_id": "pulsepass-spa", "redirect_uri": WEB + "/"}
    state = secrets.token_urlsafe(20)
    authorize = dict(client, response_type="code", scope="openid", state=state,
                     code_challenge=challenge, code_challenge_method="S256")
    browser = build_opener(HTTPCookieProcessor(CookieJar()))
    with browser.open(OIDC + "/auth?" + urlencode(authorize), timeout=20) as page:
        form = LoginForm()
        form.feed(page.read().decode())
    assert form.action, "Formulário de login ausente"
    data = urlencode({"username": user, "password": os.getenv("PULSEPASS_DEMO_PASSWORD", "pulsepass-demo-local")})
    with browser.open(form.action, data.encode(), timeout=20) as redirect:
        query = parse_qs(urlparse(redirect.url).query)
        assert query.get("state") == [state], f"Login não concluído para {user}. Confira a senha de demonstração."
    token = dict(client, grant_type="authorization_code", code=query["code"][0], code_verifier=verifier)
    with browser.open(OIDC + "/token", urlencode(token).encode(), timeout=20) as response:
        return json.load(response)["access_token"]


def api(path, token=None, body=None, status=200, key=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    request = Request(WEB + "/api/" + path, headers=headers,
                      data=json.dumps(body).encode() if body is not None else None)
    try:
        result = urlopen(request, timeout=25)
    except HTTPError as exc:
        result = exc
    with result:
        raw = result.read()
        if isinstance(status, tuple):
            assert result.status in status, f"{path}: HTTP {result.status}: {raw[:300]!r}"
            return result.status
        assert result.status == status, f"{path}: esperado {status}, recebido {result.status}: {raw[:300]!r}"
        return json.loads(raw) if raw else None


def run():
    owner, buyer = login("organizador"), login("cliente")
    name = "Teste R1 " + uuid.uuid4().hex[:8]
    events = "inventory/events"
    draft = dict(nome=name, descricao="Evento de teste HTTP", local="Auditório", dataHora="2030-01-10T21:00:00Z")
    api(events, buyer, draft, 403)
    event = api(events, owner, draft, 201)["id"]
    path = events + "/" + event
    api(path, status=404)
    api(path + "/sectors", owner, dict(nome="Plateia", preco=1500, fileiras=1, assentosPorFileira=2, acessivel=True), 201)
    api(events + "?" + urlencode({"q": name}))
    api(path + "/publish", owner, {})
    assert api(events + "?" + urlencode({"q": name}))["total"] == 1
    api(path + "/seats", status=401)
    seats = api(path + "/seats", buyer)["setores"][0]["assentos"]
    selection = dict(eventoId=event, assentoIds=[seats[0]["id"]])
    key = str(uuid.uuid4())
    order = api("orders", buyer, selection, 201, key)
    assert order["total"] == 1650 and order["status"] == "RESERVADO"
    assert api("orders", buyer, selection, 201, key) == order
    both = dict(eventoId=event, assentoIds=[seat["id"] for seat in seats])
    api("orders", buyer, both, 422, key)
    api("orders", buyer, both, 409, str(uuid.uuid4()))
    assert api(path + "/seats", buyer)["setores"][0]["assentos"][1]["status"] == "LIVRE"
    pay = "orders/" + order["id"] + "/pay"
    for suffix, expected in [("0002", 402), ("0004", 504), ("1111", 200)]:
        payload = dict(metodo="CARTAO_SIMULADO", numeroCartao="411111111111" + suffix)
        payment_key = str(uuid.uuid4())
        first = api(pay, buyer, payload, expected, payment_key)
        assert api(pay, buyer, payload, expected, payment_key) == first
    assert api("orders/" + order["id"], buyer)["status"] == "PAGO"
    assert api(path + "/seats", buyer)["setores"][0]["assentos"][0]["status"] == "VENDIDO"
    # Cada cliente usa uma chave própria e disputa o segundo assento.
    last = dict(eventoId=event, assentoIds=[seats[1]["id"]])
    def reserve_once(_):
        return api("orders", buyer, last, (201, 409), str(uuid.uuid4()))
    with ThreadPoolExecutor(max_workers=16) as workers:
        results = list(workers.map(reserve_once, range(500)))
    assert results.count(201) == 1 and results.count(409) == 499, str({n: results.count(n) for n in set(results)})
    print("OK: 500 requisições HTTP, uma reserva e 499 conflitos.")
    print("OK: login, permissões, publicação, reserva atômica, repetição de chaves e pagamento.")


if __name__ == "__main__":
    run()
