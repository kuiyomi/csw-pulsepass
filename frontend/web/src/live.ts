import {accessToken} from "./session";
export function listen(destination:string, update:()=>void) {
    let closed=false, socket:WebSocket|undefined, retry:number|undefined, renewal:number|undefined;
    async function connect() {
        try {
            const token=await accessToken();
            if(closed)return;
            const protocol=location.protocol==="https:"?"wss:":"ws:";
            const ws=new WebSocket(protocol+"//"+location.host+"/ws?access_token="+encodeURIComponent(token),"v12.stomp");
            socket=ws;
            ws.onopen=()=>ws.send("CONNECT\naccept-version:1.2\nhost:pulsepass\nAuthorization:Bearer "+token+"\n\n\0");
            ws.onmessage=e=>{
                for(const frame of String(e.data).split("\0")){
                    if(frame.trimStart().startsWith("CONNECTED")){
                        ws.send("SUBSCRIBE\nid:updates\ndestination:"+destination+"\nack:auto\nreceipt:ready\n\n\0");
                        update();
                    } else if(frame.trimStart().startsWith("MESSAGE") || frame.trimStart().startsWith("RECEIPT"))update();
                }
            };
            renewal=window.setTimeout(()=>ws.close(),60000);
            ws.onclose=()=>{clearTimeout(renewal);if(!closed)retry=window.setTimeout(connect,1200);};
        } catch {if(!closed)retry=window.setTimeout(connect,3000);}
    }
    void connect();
    return ()=>{closed=true;clearTimeout(retry);clearTimeout(renewal);socket?.close();};
}
