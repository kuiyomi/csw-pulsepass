import {useEffect, useState} from "react";
import {createRoot} from "react-dom/client";
import {account, initialize, login, logout, observeSession, accessToken} from "./session";
import {SeatMap, Checkout} from "./purchase";
import {Admin, Apply, Organizer} from "./governance";
import "./style.css";

type Summary = {id: string; nome: string; local: string; dataHora: string; precoMinimo: number; disponibilidade: string};
type Sector = {id: string; nome: string; preco: number; livres: number; capacidade: number; acessivel: boolean};
type Detail = Summary & {descricao: string; setores: Sector[]};
type Page = {itens: Summary[]; total: number};
const money = (cents: number) => (cents / 100).toLocaleString("pt-BR", {style: "currency", currency: "BRL"});
const date = (utc: string) => new Date(utc).toLocaleString("pt-BR", {dateStyle: "long", timeStyle: "short"});

function useResource<T>(url: string) {
    const [result, setResult] = useState<{url: string; value?: T; error?: string}>();
    useEffect(() => {
        const cancel = new AbortController();
        fetch(url, {signal: cancel.signal})
            .then(async response => {
                if (!response.ok) throw new Error(response.status === 404 ? "Evento não encontrado." : "Falha ao consultar o catálogo.");
                return await response.json() as T;
            })
            .then(value => setResult({url, value}))
            .catch(() => {
                if (!cancel.signal.aborted) setResult({url, error: "Não foi possível carregar os eventos."});
            });
        return () => cancel.abort();
    }, [url]);
    return result?.url === url ? result : undefined;
}

function Catalog() {
    const [query, setQuery] = useState("");
    const [page, setPage] = useState(0);
    const result = useResource<Page>("/api/inventory/events?" + new URLSearchParams({q: query, page: String(page), size: "20"}));
    return <><h1>Eventos</h1><label>Buscar por nome<input type="search" value={query} onChange={e => {setQuery(e.target.value); setPage(0);}} /></label>
        {!result && <p role="status">Carregando eventos…</p>}
        {result?.error && <p role="alert">{result.error}</p>}
        {result?.value && <><p>{result.value.total} evento(s) encontrado(s)</p><div className="grid">{result.value.itens.map(event =>
            <article key={event.id}><p>{date(event.dataHora)}</p><h2><a href={"#evento/" + event.id}>{event.nome}</a></h2><p>{event.local}</p>
            <p>{event.disponibilidade === "ABERTA" ? "Vendas abertas" : "Esgotado"} · A partir de {money(event.precoMinimo)}</p></article>)}</div>
            {result.value.total > 20 && <nav aria-label="Paginação"><button disabled={!page} onClick={() => setPage(page-1)}>Anterior</button>
            <button disabled={(page+1)*20 >= result.value.total} onClick={() => setPage(page+1)}>Próxima</button></nav>}</>}
    </>;
}

function EventDetail({id}: {id: string}) {
    const result = useResource<Detail>("/api/inventory/events/" + encodeURIComponent(id));
    const event = result?.value;
    return <><a href="#">Voltar ao catálogo</a>{!result && <p role="status">Carregando evento…</p>}
    {result?.error && <p role="alert">{result.error}</p>}{event && <><h1>{event.nome}</h1><p>{event.descricao}</p>
    <p>{date(event.dataHora)} · {event.local}</p>{account()?<a href={"#mapa/"+id}>Escolher assentos</a>:<button onClick={()=>login()}>Entrar para escolher assentos</button>}<h2>Setores</h2><div className="grid">{event.setores.map(sector =>
        <article key={sector.id}><h3>{sector.nome}</h3><p>{money(sector.preco)} · {sector.livres} de {sector.capacidade} livres</p>
        {sector.acessivel && <p>Acessível</p>}</article>)}</div></>}</>;
}

function App() {
    const [route, setRoute] = useState(location.hash);
    useEffect(() => {
        const change = () => setRoute(location.hash);
        window.addEventListener("hashchange", change);
        return () => window.removeEventListener("hashchange", change);
    }, []);
    const [user,setUser]=useState(account),[error,setError]=useState("");
    useEffect(()=>observeSession(()=>setUser(account())),[]);
    const roles=user?.realm_access?.roles??[];
    const guarded=(role:string,screen:React.ReactNode)=>roles.includes(role)?screen:<p>Entre com o perfil necessário para acessar esta página.</p>;
    let content:React.ReactNode=<Catalog/>;
    if(route.startsWith("#evento/"))content=<EventDetail key={route} id={route.slice(8)}/>;
    if(route.startsWith("#mapa/"))content=guarded("CLIENTE",<SeatMap key={route} id={route.slice(6)}/>);
    if(route.startsWith("#pedido/"))content=guarded("CLIENTE",<Checkout key={route} id={route.slice(8)}/>);
    if(route==="#organizador")content=guarded("ORGANIZADOR",<Organizer/>);
    if(route==="#solicitar")content=guarded("CLIENTE",<Apply/>);
    if(route==="#admin")content=guarded("ADMIN",<Admin/>);
    const run=(fn:()=>Promise<unknown>)=>fn().catch(e=>setError(e.message));
    return <><header><a href="#">PulsePass</a><nav><a href="#">Eventos</a>
        {roles.includes("ORGANIZADOR")?<a href="#organizador">Meus eventos</a>:user&&<a href="#solicitar">Ser organizador</a>}
        {roles.includes("ADMIN")&&<a href="#admin">Aprovações</a>}
        {user?<><span>{user.preferred_username}</span><button onClick={()=>run(()=>accessToken(true))}>Atualizar perfil</button><button onClick={()=>run(logout)}>Sair</button></>:
            <><button onClick={()=>run(()=>login())}>Entrar</button><button onClick={()=>run(()=>login(true))}>Criar conta</button></>}
        </nav></header><main>{error&&<p role="alert">{error}</p>}{content}</main></>;
}

const root=createRoot(document.getElementById("root")!);
initialize().then(()=>root.render(<App/>)).catch(error=>root.render(<main><p role="alert">{error.message}</p><a href="/">Voltar ao catálogo</a></main>));
