"""Psicólogo: secret rules everybody answers by. "rule|example|example"."""
WORDS = "jacaré|tatu|pamonha|beterraba|cuscuz|elefante|pipoca|abacaxi|girafa|panqueca|tapioca|camelo|pastel|ornitorrinco|canjica|quindim|tucano|pudim|ventilador|chinelo|bolacha|esquilo|mandioca|zebra|jabuticaba|rinoceronte|sorvete|macarrão|capivara|brigadeiro|cebola|avestruz|pinhão|canguru|lasanha|tartaruga|melancia|goiabada|pernilongo|cambuci|caracol|beijinho|salsicha|hipopótamo|maracujá|bacalhau|peixe-boi|paçoca|jiboia|mortadela|bolinho|gambá|cajuína|coxinha|urubu|farofa|besouro|cocada|pinguim|rapadura|tamanduá|torresmo|lombriga|acarajé|lhama|picolé|dromedário|biscoito|chuchu|macaxeira|quiabo|inhame|jaca|cupuaçu|tambaqui|piranha|arara|sagui|preguiça|cachorro-quente|panetone|rabanada|cavaquinho|pandeiro|berimbau|sanfona|zabumba|apito|buzina|sino|guizo|tambor|corneta".split("|")
OPEN = ["Olha só","Sinceramente","Imagina","Pois é","Nossa","Então","Escuta","Bom","Acredita","Segura essa","Fala sério","Com todo respeito","Veja bem","É o seguinte","Pensando bem","Olha, vou te falar","Pra ser sincero","Calma lá","Tá bom, mas","Falando nisso","De cara","Sem querer ofender","Ó","Cá entre nós","Para a surpresa de ninguém","Num mundo ideal","Dizem por aí que","Eu soube que","Por acaso","Aliás","Vamos combinar","Na real","Imagine só","Resumindo","Spoiler","Atenção","Cuidado","Dizem que","Sabe o que é"]
PERSONAS = {
 "um pirata":("— Gosta de café? — Arrr, só se for com rum!","— Que horas são? — Hora de zarpar, marujo!"),
 "um narrador de futebol":("— Quer água? — E lá vem ele com o copo, que lance!","— Como foi o dia? — Um jogo duríssimo, mas saímos vivos!"),
 "um jornalista de TV":("— Está com fome? — Segundo fontes, sim, e sem previsão de almoço.","— Vai sair? — Mais notícias ao longo do dia."),
 "um mordomo inglês":("— Quer um suco? — Perfeitamente, senhor.","— Vai sair? — Se for do seu agrado."),
 "um robô":("— Quer comer? — SIM. ALIMENTO. NECESSÁRIO.","— Vai dormir? — AFIRMATIVO. INICIANDO DESCANSO."),
 "uma criança de cinco anos":("— Quer comer? — Eu quero doce! Posso? Posso?","— Já fez a lição? — Por quê? Por quê?"),
 "um vendedor de rua":("— Gosta de bolo? — Olha o bolo, leva dois, é promoção!","— Vai sair? — É hoje, é hoje, aproveita!"),
 "um professor dando aula":("— Que horas são? — Vamos anotar: nove horas. Quem não anotou?","— Quer água? — Ótima pergunta, turma."),
 "um cantor de ópera":("— Está cansado? — (canta) Siiim, estou exaaausto!","— Quer café? — (canta) Quero, quero, quero!"),
 "um GPS":("— Onde você mora? — Vire à direita em duzentos metros.","— Vai sair? — Recalculando rota."),
 "um comissário de bordo":("— Quer água? — Claro, senhor, e mantenha o cinto afivelado.","— Vai sair? — Em caso de despressurização, use a máscara."),
 "um médico dando um diagnóstico":("— Estou com fome. — Hmm, quadro grave de apetite.","— Gosto de pizza. — Isso tem cura, mas não recomendo."),
 "um apresentador de auditório":("— Quer café? — Quem quer dinheiro? Quero! Quero!","— Vai sair? — Valendo um carro zero!"),
 "um padre no sermão":("— Gosta de pizza? — Meus irmãos, quem não gosta?","— Vai sair? — Vá em paz, meu filho."),
 "um chefe de cozinha bravo":("— Está bom o café? — Está uma vergonha! Refaz!","— Quer ajuda? — Sai da minha cozinha!"),
 "um detetive":("— Onde você estava ontem? — Interessante pergunta…","— Quer café? — Depende de quem está oferecendo."),
 "um astronauta":("— Quer água? — Houston, confirmo: sem gravidade, sem copo.","— Vai sair? — Preparando a decolagem."),
 "um cowboy":("— Quer café? — Pode servir, parceiro.","— Vai sair? — Vou cavalgar até o pôr do sol."),
 "um político em campanha":("— Gosta de pizza? — Meu governo vai dar pizza para todos!","— Vai sair? — Eu sempre estive ao lado do povo."),
 "um vilão de novela":("— Quer café? — Hahaha, você vai se arrepender!","— Vai sair? — Você não perde por esperar."),
}
HAND = """Responda sempre com uma pergunta|— Que horas são? — E você tem pressa?|— Gosta de café? — E quem não gosta?
Responda como se a pergunta fosse para outra pessoa da roda|— Gosta de café? — (olha para outro) Ele gosta|— Tem irmãos? — (olha para outro) Ela tem
Responda sempre começando com a última letra da pergunta|— Gosta de pizza? — Adoro|— Quer sorvete? — Ótimo
Responda sempre com duas palavras|— Gosta de pizza? — Muito, claro|— Como foi o dia? — Bem, obrigado
Responda sempre em voz baixinha, como segredo|— Quer café? — (cochicha) Quero|— Vai sair? — (cochicha) Vou
Responda sempre com “não” antes e depois “sim”|— Gosta de chocolate? — Não… sim, adoro|— Vai viajar? — Não… sim, vou
Responda sempre elogiando a pergunta|— Gosta de chuva? — Que pergunta incrível! Gosto|— Que horas são? — Ótima pergunta, nove
Responda fingindo que está com pressa|— Quer água? — Rápido, quero!|— Como foi o dia? — Corrido, tchau
Responda sempre repetindo a última palavra da pergunta|— Gosta de sol? — Sol, gosto|— Vai ao cinema? — Cinema, vou
Responda como se estivesse dormindo|— Quer comer? — Hmm… zzz… quero|— Tudo bem? — Zzz… tudo
Responda sempre apontando para alguém|— Quem come mais? — (aponta) Ele|— Quem dorme mais? — (aponta) Ela
Responda como se fosse a melhor notícia do mundo|— Quer água? — Que maravilha, quero!|— Vai chover? — Que notícia incrível!
Responda como se fosse a pior notícia do mundo|— Quer sorvete? — Ai, que desgraça…|— Hoje é sexta? — Ai, que tragédia
Responda sempre dando uma nota de 0 a 10|— Gosta de pizza? — Nota 9|— Foi bom? — Nota 7
Responda sempre mudando de assunto|— Gosta de café? — Aliás, viu o tempo?|— Quer pizza? — Falando em tempo, tá quente
Responda sempre dizendo o nome de quem perguntou|— Quer água? — Quero, Ana|— Vai sair? — Vou, Pedro
Responda sempre com um “mas” no meio|— Gosta de pizza? — Gosto, mas depende|— Vai sair? — Vou, mas tarde
Responda sempre com uma comparação|— Está quente? — Quente que nem forno|— Gosta de pizza? — Mais que de pão
Responda sempre com um verbo no passado|— Quer comer? — Comi ontem|— Vai viajar? — Viajei semana passada
Responda sempre com um verbo no futuro|— Gosta de chuva? — Gostarei|— Quer café? — Tomarei
Responda sem usar sim nem não|— Gosta de pizza? — Adoro|— Vai sair? — Vou
Responda sempre olhando para o teto|— Quer água? — (olha pro teto) Quero|— Vai sair? — (olha pro teto) Vou
Responda sempre com a mão no queixo|— Gosta de café? — (pensativo) Hmm, gosto|— Vai viajar? — (pensativo) Talvez
Responda como se estivesse no telefone|— Gosta de pizza? — Alô? Gosto sim|— Vai sair? — Alô? Vou
Responda como se estivesse em um navio balançando|— Quer comer? — (balança) Quero|— Tudo bem? — (balança) Mais ou menos
Responda sempre usando o nome de um país|— Quer café? — Brasil, quero|— Vai viajar? — Itália, vou
Responda sempre falando um número no fim|— Que horas são? — Nove, 5|— Gosta de pizza? — Gosto, 7
Responda sempre rimando com a pergunta|— Quer pão? — Quero, não é questão|— Vai ao mar? — Vou, sem hesitar
Responda sempre com “quem sabe”|— Gosta de pizza? — Quem sabe|— Vai sair? — Quem sabe
Responda sempre tirando sarro|— Quer ajuda? — Claro, você? Haha|— Gosta de café? — Adoro, o seu então
Responda sempre contando até três|— Quer água? — Um, dois, três… quero|— Vai sair? — Um, dois, três… vou
Responda sempre com o nome de um filme|— Gosta de pizza? — Titanic, gosto|— Vai sair? — Shrek, vou
Responda sempre com o nome de uma música|— Gosta de café? — Garota de Ipanema, gosto|— Vai sair? — Evidências, vou
Responda como se tivesse medo|— Quer comer? — (tremendo) Q-quero|— Vai sair? — (tremendo) V-vou""".split("\n")
def build():
    out=list(HAND)
    for w in WORDS: out.append(f"Termine toda resposta com “{w}”|— Que horas são? — Nove, {w}|— Gosta de café? — Gosto, {w}")
    for o in OPEN: out.append(f"Comece toda resposta com “{o}”|— Que horas são? — {o}, nove|— Gosta de café? — {o}, gosto")
    for p,(e1,e2) in PERSONAS.items(): out.append(f"Responda como se fosse {p}|{e1}|{e2}")
    for w in WORDS: out.append(f"Use a palavra “{w}” em toda resposta|— Que horas são? — {w}, são nove|— Quer comer? — Quero, só {w}")
    return out
if __name__=='__main__': print(len(build()))
