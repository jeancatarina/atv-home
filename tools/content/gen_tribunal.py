"""Tribunal dos Amigos: "case|8 places|8 things|8 times". Each kit has its own places, things and hours;
titles are crime templates x objects that belong to the kit."""
KITS = [
 ("casa", "cozinha,sala,quintal,garagem,varanda,escritório,corredor,lavanderia", "garfo,vela,colher,prato,balão,guardanapo,faca,caixa de presente", "15h,16h,17h,18h,19h,20h,21h,22h",
  ["o|bolo de aniversário","a|última fatia de pizza","o|controle da TV","a|chave do portão","o|pote de sorvete","a|meia da sorte","o|carregador do celular","a|garrafa de refrigerante","o|pacote de biscoito","a|lâmpada da sala","o|chinelo favorito","a|manta do sofá","o|rolo de papel higiênico"]),
 ("escritório", "recepção,copa,sala de reunião,corredor,sala do chefe,banheiro,estacionamento,arquivo", "caneta,grampeador,caderno,xícara,fita adesiva,pasta,mouse,calculadora", "8h,9h,10h,11h,12h,13h,14h,15h",
  ["o|pen drive da apresentação","a|caneca do chefe","o|último café","a|planilha do relatório","o|crachá do estagiário","a|bala da recepção","o|cabo do projetor","a|senha do Wi-Fi","o|pão de queijo da copa","a|cadeira boa","o|guarda-chuva da empresa","a|marmita da geladeira"]),
 ("escola", "pátio,biblioteca,cantina,quadra,sala de aula,corredor,secretaria,banheiro", "giz,mochila,apagador,caderno,estojo,régua,bola,lanche", "7h,8h,9h,10h,11h,12h,13h,14h",
  ["a|prova de matemática","o|apito do professor","a|bola da quadra","o|lanche do recreio","o|trabalho de ciências","a|chave do armário","o|estojo novo","a|caderneta","o|giz colorido","a|merenda","o|boletim","a|lição de casa"]),
 ("festa", "pista,bar,varanda,cozinha,jardim,banheiro,entrada,chapelaria", "copo,chapéu,confete,balão,microfone,máscara,colar,serpentina", "20h,21h,22h,23h,meia-noite,1h,2h,3h",
  ["o|bolo de casamento","a|caixa de som","o|presente do aniversariante","a|playlist","o|último salgadinho","a|fantasia de gorila","o|DJ","a|chave do carro do noivo","o|microfone do karaokê","a|torta de limão","o|chapéu do anfitrião","a|garrafa de espumante"]),
 ("viagem", "recepção,piscina,quarto,restaurante,praia,lobby,estacionamento,elevador", "mala,boné,protetor solar,chinelo,toalha,mapa,câmera,óculos de sol", "7h,8h,9h,10h,11h,14h,16h,18h",
  ["a|mala de mão","o|guia de passeios","a|máquina fotográfica","o|protetor solar","o|chinelo da piscina","a|chave do quarto","o|mapa do passeio","a|toalha de praia","o|snorkel","a|reserva do restaurante","o|boné da excursão","a|cadeira de praia"]),
 ("restaurante", "cozinha,salão,balcão,despensa,banheiro,calçada,estoque,mesa 7", "avental,cardápio,bandeja,faca,panela,prato,guardanapo,taça", "11h,12h,13h,14h,19h,20h,21h,22h",
  ["a|receita secreta","o|último pudim","a|caixa registradora","o|vinho da casa","a|gorjeta","o|avental do chef","a|conta da mesa 7","o|molho especial","a|reserva do aniversário","o|tempero misterioso","a|bandeja de sobremesas","o|cardápio novo"]),
 ("clube", "piscina,quadra,sauna,vestiário,churrasqueira,sala de jogos,salão,bar", "toalha,raquete,bola,boia,touca,chinelo,apito,garrafa", "8h,9h,10h,11h,14h,15h,16h,17h",
  ["a|raquete de tênis","o|troféu do campeonato","a|boia do jacaré","o|apito do salva-vidas","o|bolo do clube","a|touca de natação","o|ticket da churrasqueira","a|bola de vôlei","o|cadeado do armário","a|toalha listrada","o|relógio do vestiário","a|medalha do torneio","o|apito do árbitro"]),
 ("condomínio", "portaria,garagem,salão de festas,playground,elevador,piscina,terraço,lixeira", "chave,carrinho,capacho,vaso,bicicleta,cadeado,sacola,recibo", "6h,7h,8h,12h,17h,18h,19h,22h",
  ["a|bicicleta da garagem","o|vaso da portaria","o|carrinho de compras","a|encomenda do vizinho","o|capacho novo","a|chave do salão","o|jornal de domingo","o|guarda-chuva da portaria","a|planta do hall","o|cadeado da bicicleta","a|caixa de correio","a|vaga do 302"]),
 ("sítio", "curral,horta,pomar,varanda,riacho,galpão,churrasqueira,cozinha", "enxada,balde,chapéu,botina,corda,lampião,cesta,peneira", "5h,6h,7h,9h,12h,15h,17h,19h",
  ["o|galo de estimação","a|melancia gigante","o|queijo da vovó","a|rede da varanda","o|doce de leite","a|chave do galpão","o|trator de brinquedo","a|cesta de ovos","o|chapéu do vovô","a|bicicleta antiga","o|violão da fogueira","a|jabuticaba mais bonita"]),
 ("praia", "quiosque,calçadão,areia,barraca,píer,estacionamento,rochas,passarela", "boia,prancha,cooler,canga,chapéu,balde,cadeira,raquete", "8h,9h,10h,11h,13h,14h,15h,16h",
  ["a|prancha de surf","o|cooler de cerveja","a|raquete de frescobol","a|barraca listrada","o|chapéu de palha","o|castelo de areia","a|canga estampada","o|protetor solar","o|sorvete de coco","a|bola de vôlei","a|cadeira de praia","a|garrafa térmica"]),
]
def build():
    out=[]
    for name,places,things,times,objs in KITS:
        for o in objs:
            art,obj=o.split("|")
            a="do" if art=="o" else "da"
            out.append(f"O sumiço {a} {obj}|{places}|{things}|{times}")
            out.append(f"O mistério {a} {obj}|{places}|{things}|{times}")
    return out
if __name__=='__main__': o=build(); print(len(o)); print(o[0])
