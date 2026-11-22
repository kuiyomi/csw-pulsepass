import {useEffect,useRef,useState} from "react";
import {api,ApiError} from "./session";
import {listen} from "./live";

type Seat={id:string;fileira:string;numero:number;status:string};
type Sector={setorId:string;nome:string;preco:number;assentos:Seat[]};
type Order={id:string;status:string;expiraEm:string;total:number;subtotal:number;taxa:number;itens:{assentoId:string;setorNome:string;preco:number}[]};
export const money=(n:number)=>(n/100).toLocaleString("pt-BR",{style:"currency",currency:"BRL"});
const message=(e:unknown)=>e instanceof Error?e.message:"Falha de conexão.";
export function SeatMap({id}:{id:string}){
    const [sectors,setSectors]=useState<Sector[]>([]),[selected,select]=useState<string[]>([]),[error,setError]=useState(""),[busy,working]=useState(false);
    const attempt=useRef({signature:"",key:""});
    useEffect(()=>{
        let active=true,version=0;
        const load=async()=>{
            const n=++version;
            try{
                const map=await api<{setores:Sector[]}>("/api/inventory/events/"+id+"/seats");
                if(!active||n!==version)return;
                setSectors(map.setores);
                const free=new Set(map.setores.flatMap(s=>s.assentos.filter(a=>a.status==="LIVRE").map(a=>a.id)));
                select(prev=>prev.filter(a=>free.has(a)));
            }catch(e){if(active)setError(message(e));}
        };
        void load();const stop=listen("/topic/events/"+id+"/seats",load);
        return ()=>{active=false;stop();};
    },[id]);
    async function reserve(){
        working(true);setError("");
        const signature=selected.slice().sort().join(",");
        if(attempt.current.signature!==signature)attempt.current={signature,key:crypto.randomUUID()};
        try{
            const order=await api<Order>("/api/orders","POST",{eventoId:id,assentoIds:selected},attempt.current.key);
            location.hash="pedido/"+order.id;
        }catch(e){setError(message(e));if(e instanceof ApiError && e.status===409 && !e.retryable)attempt.current.signature="";}
        finally{working(false);}
    }
    return <><a href={"#evento/"+id}>Voltar ao evento</a><h1>Escolha seus assentos</h1>
    <p>Livre · ✓ Selecionado · ▧ Reservado · × Vendido</p><div className="stage">Palco</div>
    {sectors.map(s=><section key={s.setorId}><h2>{s.nome} · {money(s.preco)}</h2><div className="seat-list">{s.assentos.map(a=>{
        const picked=selected.includes(a.id);
        return <button key={a.id} aria-pressed={picked} title={a.status} disabled={busy||a.status!=="LIVRE"||(!picked&&selected.length>=8)}
        onClick={()=>select(prev=>picked?prev.filter(x=>x!==a.id):[...prev,a.id])}>{a.fileira}{a.numero} {picked?"✓":a.status==="RESERVADO"?"▧":a.status==="VENDIDO"?"×":""}</button>;
    })}</div></section>)}
    {error&&<p role="alert">{error}</p>}<button disabled={!selected.length||busy} onClick={reserve}>Reservar ({selected.length}/8)</button></>;
}

export function Checkout({id}:{id:string}){
    const [order,set]=useState<Order|null>(null),[error,err]=useState(""),[card,change]=useState(""),[now,tick]=useState(Date.now()),[busy,working]=useState(false);
    const attempt=useRef({card:"",key:""});
    useEffect(()=>{
        let active=true,version=0;
        async function load(){const n=++version;try{const r=await api<Order>("/api/orders/"+id);if(active&&n===version)set(r);}
            catch(e){if(active)err(message(e));}}
        void load();const stop=listen("/user/queue/orders",load);
        const clock=window.setInterval(()=>tick(Date.now()),500),refresh=window.setInterval(load,5000);
        return ()=>{active=false;stop();clearInterval(clock);clearInterval(refresh);};
    },[id]);
    async function pay(){
        working(true);err("");
        if(attempt.current.card!==card)attempt.current={card,key:crypto.randomUUID()};
        try{
            await api("/api/orders/"+id+"/pay","POST",{metodo:"CARTAO_SIMULADO",numeroCartao:card},attempt.current.key);
            set(await api<Order>("/api/orders/"+id));change("");
        }catch(e){err(message(e));if(e instanceof ApiError && [402,504].includes(e.status))attempt.current.card="";}
        finally{working(false);}
    }
    const seconds=order?Math.max(0,Math.ceil((Date.parse(order.expiraEm)-now)/1000)):0;
    return <><a href="#">Voltar aos eventos</a><h1>{order?.status==="PAGO"?"Pagamento confirmado":"Meu pedido"}</h1>
    {order&&<><p>{order.status}</p>{order.itens.map(i=><p key={i.assentoId}>{i.setorNome} · {money(i.preco)}</p>)}
    <p>Subtotal {money(order.subtotal)} · Taxa {money(order.taxa)}</p><h2>Total {money(order.total)}</h2>
    {order.status==="RESERVADO"&&<><p>Sua reserva expira em {Math.floor(seconds/60)}:{String(seconds%60).padStart(2,"0")}</p>
    <progress value={seconds} max={600}/><label>Cartão simulado<input inputMode="numeric" value={card} onChange={e=>change(e.target.value)} placeholder="4111111111111111"/></label>
    <button disabled={busy||!seconds||!/^\d{13,19}$/.test(card)} onClick={pay}>Confirmar pagamento</button></>}</>}
    {error&&<p role="alert">{error}</p>}</>;
}
