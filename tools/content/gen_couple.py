"""Sintonia de Casal: choice questions from categories x phrasings, and scale questions from traits x situations."""
CATS = """prato|Italiano|Japonês|Churrasco|Comida caseira
lanche|Pizza|Hambúrguer|Pastel|Sanduíche natural
sobremesa|Brigadeiro|Pudim|Sorvete|Fruta
bebida|Cerveja|Drink|Refrigerante|Água
café da manhã|Pão com manteiga|Tapioca|Frutas|Só café
sabor de sorvete|Chocolate|Morango|Creme|Limão
tipo de filme|Comédia|Terror|Romance|Ação
gênero de série|Drama|Suspense|Comédia|Documentário
estilo de música|Sertanejo|Pop|Rock|MPB
instrumento|Violão|Piano|Bateria|Nenhum
esporte|Futebol|Vôlei|Natação|Corrida
destino de viagem|Praia|Montanha|Cidade grande|Campo
tipo de hospedagem|Hotel|Pousada|Casa alugada|Camping
meio de transporte|Carro|Avião|Trem|Bicicleta
estação do ano|Verão|Outono|Inverno|Primavera
horário do dia|Manhã|Tarde|Noite|Madrugada
animal de estimação|Cachorro|Gato|Pássaro|Peixe
superpoder|Voar|Ficar invisível|Ler mentes|Parar o tempo
profissão|Médico|Professor|Artista|Empresário
tipo de casa|Apartamento|Casa com quintal|Sítio|Casa na praia
cômodo da casa|Cozinha|Sala|Quarto|Varanda
programa de sábado|Churrasco|Cinema|Balada|Ficar em casa
roupa para sair|Jeans|Vestido ou social|Bermuda|Esportiva
cor|Azul|Vermelho|Verde|Preto
matéria da escola|Português|Matemática|Educação Física|Artes
brincadeira de criança|Esconde-esconde|Pega-pega|Bola|Videogame
jogo de tabuleiro|Banco Imobiliário|Detetive|Dominó|Xadrez
rede social|Instagram|WhatsApp|YouTube|Nenhuma
aplicativo|Streaming|Jogos|Mapas|Música
presente|Viagem|Eletrônico|Roupa|Experiência
tipo de festa|Churrasco|Balada|Aniversário infantil|Reunião pequena
tipo de livro|Romance|Suspense|Autoajuda|Quadrinhos
carro dos sonhos|Esportivo|Picape|Elétrico|Clássico
tipo de pão|Francês|Integral|Pão de queijo|Baguete
tipo de queijo|Mussarela|Minas|Parmesão|Gorgonzola
fruta|Banana|Manga|Uva|Melancia
verdura|Brócolis|Cenoura|Alface|Nenhuma
tempero|Alho|Pimenta|Coentro|Orégano
churrasco|Picanha|Linguiça|Frango|Queijo coalho
pizza|Calabresa|Mussarela|Portuguesa|Doce
massa|Lasanha|Espaguete|Nhoque|Pizza
doce de festa|Brigadeiro|Beijinho|Cajuzinho|Bolo
bolo|Chocolate|Cenoura|Fubá|Laranja
chocolate|Ao leite|Branco|Meio amargo|Com recheio
bar|Barzinho|Boteco|Restaurante|Em casa
tipo de academia|Musculação|Dança|Luta|Nenhuma
modo de relaxar|Dormir|Série|Música|Passear
lugar para ler|Sofá|Cama|Praça|Biblioteca
jeito de acordar|Despertador|Sol|Barulho da casa|Só quando quer
tipo de banho|Quente|Frio|Morno|Demorado
tipo de cabelo preferido|Curto|Comprido|Raspado|Preso
tipo de chuva|Garoa|Tempestade|Chuva de verão|Nenhuma
pizzaria|Rodízio|Delivery|Tradicional|Em casa
programa de domingo|Almoço em família|Praia|Cama|Passeio
manhã de folga|Dormir|Café na rua|Arrumar a casa|Academia
primeira coisa ao acordar|Celular|Café|Banho|Esticar
lembrança de infância|Férias|Brincar na rua|Doces|Colo de alguém
super-herói|Super-Homem|Mulher-Maravilha|Homem-Aranha|Nenhum
vilão|Coringa|Darth Vader|Voldemort|Nenhum
personagem de desenho|Mickey|Pato Donald|Bob Esponja|Tom e Jerry
parque|Disney|Beto Carrero|Hopi Hari|Parque da cidade
ilha|Fernando de Noronha|Ilhabela|Maldivas|Ilha deserta
cidade|São Paulo|Rio|Florianópolis|Salvador
país|Itália|Japão|Estados Unidos|Portugal
refeição do dia|Café|Almoço|Jantar|Lanche da tarde
dia da semana|Sexta|Sábado|Domingo|Segunda
mês do ano|Janeiro|Julho|Dezembro|Setembro
data comemorativa|Natal|Carnaval|Aniversário|Réveillon
tipo de chocolate quente|Cremoso|Aguado|Com marshmallow|Nenhum
modelo de celular|iPhone|Android|Qualquer um|O mais barato
fone de ouvido|Com fio|Sem fio|Caixa de som|Nenhum
lugar para estudar|Biblioteca|Em casa|Café|Parque
tipo de transporte público|Ônibus|Metrô|Trem|Nenhum
jeito de se divertir|Dançando|Conversando|Jogando|Comendo
tipo de amigo|Engraçado|Fiel|Aventureiro|Calado
virtude|Paciência|Humor|Lealdade|Criatividade
defeito|Teimosia|Bagunça|Atraso|Ciúme
medo|Altura|Baratas|Falar em público|Escuro
mania|Roer unha|Falar sozinho|Mexer no cabelo|Estalar os dedos
hábito matinal|Café|Notícias|Alongar|Redes sociais
reação a surpresa|Grita|Chora|Fica sem reação|Ri
tipo de risada|Alta|Baixa|De nariz|Sem som
jeito de pedir desculpa|Pessoalmente|Mensagem|Presente|Fazendo uma comida
jeito de comemorar|Festão|Jantar|Viagem|Quieto em casa
jeito de economizar|Guardando tudo|Cortando supérfluos|Pechinchando|Nenhum
jeito de gastar|Experiências|Roupas|Comida|Eletrônicos
herança dos sonhos|Casa|Dinheiro|Fazenda|Joias
sonho de consumo|Casa própria|Viagem pelo mundo|Carro|Negócio próprio
primeira compra ao ganhar na loteria|Casa|Carro|Viagem|Doar
trabalho dos sonhos|Artista|Dono do próprio negócio|Esportista|Cientista
cargo|Chefe|Funcionário|Autônomo|Aposentado
horário de trabalho|Manhã|Tarde|Noite|Flexível
local de trabalho|Escritório|Casa|Café|Rua
tipo de reunião|Presencial|Online|Por mensagem|Nenhuma
tipo de chefe|Parceiro|Exigente|Ausente|Engraçado
tipo de colega|Fofoqueiro|Quieto|Animado|Preguiçoso
tipo de professor|Divertido|Rígido|Distraído|Exigente
tipo de vizinho|Barulhento|Silencioso|Fofoqueiro|Gente boa
tipo de dono de casa|Organizado|Bagunceiro|Detalhista|Relaxado
tipo de passageiro|Dorme|Conversa|Dá palpite|Escolhe a música
tipo de motorista|Calmo|Estressado|Devagar|Apressado
tipo de cozinheiro|Experimental|Tradicional|Preguiçoso|Chef
tipo de cliente|Indeciso|Exigente|Simpático|Apressado
tipo de passeio|Museu|Parque|Shopping|Bar
tipo de show|Pequeno|Estádio|Festival|Em casa
tipo de trilha|Fácil|Difícil|Com cachoeira|Nenhuma
tipo de praia|Deserta|Cheia|Com barraca|De piscina natural
tipo de clima|Calor|Frio|Chuva|Ameno
tipo de festa de aniversário|Surpresa|Com bolo em casa|Num bar|Nenhuma
tipo de amigo secreto|Presente barato|Presente engraçado|Presente útil|Nenhum
lugar para morar|Campo|Praia|Cidade grande|Cidade pequena
lugar para um jantar romântico|Restaurante chique|Em casa|Piquenique|Pizzaria
lugar para passar a noite|Hotel|Casa de amigos|Camping|Carro
lugar para uma sesta|Rede|Sofá|Cama|Banco da praça
lugar para guardar segredo|Diário|Celular|Na cabeça|Com um amigo
lugar para ficar sozinho|Quarto|Varanda|Carro|Rua
lugar para se esconder|Armário|Debaixo da cama|Banheiro|Atrás da porta""".split("\n")
PHR_ALL = [
 "Qual {cat} {nome} escolheria?",
 "Qual {cat} {nome} jamais escolheria?",
 "Qual {cat} {nome} escolheria num dia ruim?",
 "Qual {cat} {nome} escolheria para agradar os outros?",
 "Qual {cat} {nome} escolheria se fosse de graça?",
 "Qual {cat} {nome} escolhia quando era criança?",
 "Qual {cat} {nome} escolheria se fosse só uma vez na vida?",
 "Qual {cat} {nome} pediria de presente?",
 "Qual {cat} {nome} escolheria numa viagem?",
 "Qual {cat} {nome} escolheria com os amigos?",
 "Qual {cat} {nome} escolheria sozinho?",
 "Qual {cat} {nome} escolheria com a família?",
 "Qual {cat} {nome} escolheria no verão?",
 "Qual {cat} {nome} escolheria no inverno?",
 "Qual {cat} {nome} escolheria num domingo?",
 "Qual {cat} {nome} repetiria a vida inteira?",
 "Qual {cat} {nome} escolheria num dia de festa?",
]
TRAITS = """paciente|Estressado|Zen
organizado|Bagunceiro|Impecável
corajoso|Medroso|Sem medo
curioso|Desinteressado|Curiosíssimo
ciumento|Tranquilo|Ciumento demais
vaidoso|Nem aí|Muito vaidoso
preguiçoso|Elétrico|Sofá é vida
ansioso|Zen|Roendo unha
dramático|Calmo|Novela mexicana
teimoso|Flexível|Cabeça dura
pontual|Sempre atrasado|Sempre antes da hora
sociável|Quieto|Dono da festa
engraçado|Sério|Palhaço
generoso|Mão de vaca|Dá tudo
esquecido|Memória de elefante|Esquece tudo
competitivo|Tanto faz|Vive para ganhar
romântico|Nada romântico|Muito romântico
criativo|Sem ideias|Cheio de ideias
bagunceiro|Arrumado|Caos
bom de papo|Calado|Papo infinito
desligado|Atento|No mundo da lua
supersticioso|Nada|Muito
bom de cozinha|Queima água|Chef
guloso|Come pouco|Come tudo
exigente|Come qualquer coisa|Cheio de frescura
dorminhoco|Quase não dorme|Dorme o dia todo
fofoqueiro|Nunca fala|Sabe de tudo
animado|Desanimado|Pilhado
sensível|Casca grossa|Chora fácil
decidido|Indeciso|Sabe o que quer
gastador|Econômico|Gasta tudo
sistemático|Improvisa|Tudo no horário
exagerado|Contido|Exagera em tudo
aventureiro|Caseiro|Radical
tímido|Desinibido|Cara de pau
bom de direção|Perigo|Piloto
bom de tecnologia|Pede ajuda|Resolve tudo
inteligente|Ingênuo|Esperto
confiável|Some na hora|Pode contar
bom ouvinte|Interrompe|Escuta tudo
carinhoso|Seco|Muito carinhoso
honesto|Conta mentirinhas|Sempre sincero
mal-humorado|Sempre bem|De mau humor
fresco|Topa tudo|Cheio de frescura
exigente com limpeza|Nem liga|Obsessivo
rápido para se arrumar|Uma eternidade|Cinco minutos
bom em esportes|Sedentário|Atleta
bom de dança|Pé de chumbo|Pé de valsa
bom de canto|Desafinado|Profissional
bom em jogos|Perde sempre|Imbatível
distraído|Foca em tudo|Vive distraído
bom de matemática|Só na calculadora|Faz de cabeça
bom de desenho|Só risco|Artista
bom em dar presente|Péssimo|Perfeito
bom em guardar segredo|Conta tudo|Um túmulo
bom de memória|Esquece|Lembra de tudo
bom em orientação|Se perde|GPS humano
bom em acordar cedo|Impossível|Madrugador
bom em lidar com criança|Foge|Adora
bom em acampar|Odeia|Adora
bom em lidar com bicho|Medo|Adora
bom de improviso|Trava|Se vira
bom de festa|Some|Dono da pista
bom em jogo de tabuleiro|Perde|Mestre
bom em perder|Chora|Perde rindo
bom em ganhar|Humilde|Se gaba
bom em pedir desculpas|Orgulhoso|Pede na hora
leal|Traidor|Fiel até o fim
paquerador|Tímido|Galanteador
cuidadoso|Descuidado|Cuidadoso demais
radical|Moderado|Extremista
sonhador|Realista|Vive no mundo da lua
prático|Complicado|Resolve tudo rápido
inquieto|Parado|Não para um minuto
metódico|Desorganizado|Segue o passo a passo
fofo|Durão|Derrete fácil
formal|Relaxado|Sempre de terno
falante|Calado|Fala pelos cotovelos
observador|Distraído|Repara em tudo
chorão|Durão|Chora de tudo
risonho|Sério|Ri de tudo
pão-duro|Esbanjador|Conta cada centavo
vaidoso com a roupa|Veste qualquer coisa|Só sai arrumado
despojado|Cheio de pompa|Simples demais
caprichoso|Faz de qualquer jeito|Faz com capricho
valente|Foge de briga|Entra em qualquer briga
dormilão|Acorda cedo|Dorme até tarde
saudosista|Só olha pra frente|Vive do passado
nostálgico|Esquece o passado|Guarda tudo da infância
tagarela|Reservado|Conta tudo para todos
elegante|Desengonçado|Sempre impecável
brincalhão|Sisudo|Faz graça com tudo
carente|Independente|Precisa de atenção
independente|Dependente|Faz tudo sozinho
mão aberta|Mão fechada|Doa o que tem
orgulhoso|Humilde|Nunca pede desculpa
curioso com tecnologia|Desconfiado|Testa tudo
fanático por esporte|Detesta esporte|Vive o esporte
caloroso|Frio|Abraça todo mundo
introvertido|Extrovertido|Precisa de tempo sozinho
cismado|Despreocupado|Pensa em tudo mil vezes
impulsivo|Pensa antes|Age sem pensar
rancoroso|Perdoa fácil|Guarda mágoa
pacificador|Provocador|Acalma todo mundo
mandão|Obediente|Quer mandar em tudo
medroso com bicho|Ama bicho|Foge de tudo que se mexe
aventureiro na comida|Come sempre igual|Prova de tudo
bom de papo com estranhos|Tímido com estranhos|Conversa com qualquer um
organizado com dinheiro|Bagunça as contas|Controla cada centavo
dado a surpresas|Odeia surpresa|Vive criando surpresa
perfeccionista|Faz de qualquer jeito|Refaz até ficar perfeito
bom anfitrião|Péssimo anfitrião|Recebe todo mundo bem
alegre de manhã|Rabugento de manhã|Acorda cantando
noturno|Diurno|Vive de madrugada
caseiro com a cozinha|Pede comida|Cozinha pra todo mundo
maratonista de séries|Não vê série|Vê tudo numa noite
leitor|Nunca lê|Devora livros
ouvinte de podcast|Não ouve|Só ouve podcast
tecnológico|Analógico|Vive com o celular
sofredor|Otimista|Vê o lado ruim de tudo
otimista|Pessimista|Acha que tudo vai dar certo
estrategista|Improvisador|Planeja tudo
sortudo|Azarado|Tudo dá certo""".split("\n")
SIT = ["no trânsito","numa fila","em viagem","numa discussão","cozinhando","no trabalho","com crianças","num jogo","de manhã","numa festa","no calor","com dinheiro","dentro de casa","num dia de chuva","num domingo","com os amigos","com a família","numa mudança","na hora de decidir","ao acordar","sob pressão","sem dormir","com fome","de madrugada"]
PHR = ["Qual {cat} {nome} escolheria?","Qual {cat} {nome} jamais escolheria?"]
FOOD = {"prato","lanche","sobremesa","bebida","café da manhã","sabor de sorvete","doce de festa","bolo","chocolate","pizza","massa","churrasco","fruta"}
PHR_FOOD = ["Qual {cat} {nome} pediria num dia ruim?","Qual {cat} {nome} comeria de madrugada?"]
# only concrete things people really pick between; "tipo de amigo", "virtude", "mania"... read badly in these phrasings
CONCRETE = {"prato","lanche","sobremesa","bebida","café da manhã","sabor de sorvete","tipo de filme","gênero de série","estilo de música","instrumento","esporte","destino de viagem","tipo de hospedagem","meio de transporte","estação do ano","horário do dia","animal de estimação","superpoder","profissão","tipo de casa","programa de sábado","roupa para sair","cor","brincadeira de criança","jogo de tabuleiro","presente","tipo de festa","tipo de livro","fruta","tempero","churrasco","pizza","massa","doce de festa","bolo","chocolate","país","cidade","dia da semana","mês do ano","data comemorativa","modo de relaxar","jeito de acordar","programa de domingo","super-herói","personagem de desenho","refeição do dia","lugar para morar","lugar para um jantar romântico","lugar para uma sesta"}
TEMPERAMENT = {"paciente","organizado","ansioso","dramático","teimoso","pontual","sociável","engraçado","generoso","esquecido","competitivo","criativo","bagunceiro","desligado","exigente","animado","sensível","decidido","exagerado","tímido","mal-humorado","fresco","carinhoso","distraído","impulsivo","cuidadoso","falante","observador"}
SIT2 = ["no trânsito","numa fila","em viagem","numa discussão","no trabalho","com crianças","num jogo","de manhã","numa festa","no calor","com dinheiro","sob pressão","sem dormir","com fome","de madrugada","numa mudança"]
def build():
    out=[]
    for line in CATS:
        cat,*opts=line.split("|")
        if cat not in CONCRETE: continue
        for ph in PHR + (PHR_FOOD if cat in FOOD else []): out.append("C|"+ph.format(cat=cat.replace("churrasco","carne de churrasco"),nome="{nome}")+"|"+"|".join(opts))
    for line in TRAITS:
        adj,lo,hi=line.split("|")
        out.append(f"S|Quão {adj} é {{nome}}?|{lo}|{hi}")
        if adj in TEMPERAMENT:
            for s in SIT2: out.append(f"S|Quão {adj} é {{nome}} {s}?|{lo}|{hi}")
    return out

if __name__=='__main__':
    q=build(); print(len(q)); print(*q[:3],q[-1],sep='\n')
