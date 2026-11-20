"""Frequência: two poles of a scale, and the clue is spoken, so the axes have to be things people can argue about.
   Each category lists the axes that make sense for it: "Pior {x}|Melhor {x}", "{x} mais barato|{x} mais caro"..."""
AXES = {
 "good":  ("Pior {c}", "Melhor {c}"),
 "price": ("{c} barato", "{c} caro"),
 "scary": ("{c} nada assustador", "{c} assustador"),
 "weird": ("{c} normal", "{c} esquisito"),
 "hard":  ("{c} fácil", "{c} difícil"),
 "classy":("{c} brega", "{c} chique"),
 "kid":   ("{c} de criança", "{c} de adulto"),
 "cozy":  ("{c} nada romântico", "{c} romântico"),
 "fun":   ("{c} chato", "{c} divertido"),
 "rare":  ("{c} comum", "{c} raro"),
 "gross": ("{c} nojento", "{c} delicioso"),
}
# category -> axes that read well with it
CATS = {
 "sabor de pizza":"good weird","sabor de sorvete":"good weird","sobremesa":"good classy","prato de festa":"good classy","comida de rua":"good price","lanche":"good price gross","café da manhã":"good classy",
 "bebida":"good classy","fruta":"good gross","verdura":"good gross","tempero":"good gross","doce de festa":"good kid","comida de boteco":"good price","comida de avião":"good gross","comida de hospital":"good gross",
 "animal de estimação":"good weird kid","bicho de zoológico":"scary rare kid","animal da fazenda":"good kid","inseto":"scary gross","bicho do mar":"scary weird","dinossauro":"scary kid","personagem de desenho":"good kid weird",
 "profissão":"good hard classy","emprego de verão":"good fun","matéria da escola":"good hard fun","esporte":"hard fun classy","jogo de tabuleiro":"hard fun kid","videogame":"hard fun kid","brincadeira de criança":"fun kid",
 "dia da semana":"good fun","mês do ano":"good fun","estação do ano":"good cozy","horário do dia":"good cozy","feriado":"good fun","data comemorativa":"good fun kid","festa":"fun classy kid","festa de criança":"fun weird",
 "viagem":"good price hard cozy","destino de lua de mel":"price cozy classy","hotel":"price classy","hospedagem":"price classy cozy","passeio":"fun hard cozy","praia":"good classy","cidade":"good classy","país":"good price rare",
 "meio de transporte":"good fun price","carro":"classy price","presente":"good price cozy weird","presente de amigo secreto":"good price weird","lembrança de viagem":"good price weird","roupa":"classy price weird","sapato":"classy price",
 "cabelo":"classy weird","corte de cabelo":"classy weird","tatuagem":"classy weird","apelido":"fun weird kid","nome de pessoa":"good classy rare","nome de bicho de estimação":"good weird","nome de banda":"good weird",
 "filme":"good scary weird cozy","série":"good fun cozy","novela":"good fun cozy","desenho animado":"good kid weird","livro":"good hard fun","música":"good fun cozy","estilo de música":"good fun","cantor":"good classy","show":"good price fun",
 "instrumento":"hard classy","dança":"hard fun","brinquedo":"good kid weird","aplicativo":"good fun","rede social":"good fun","jogo de celular":"good fun hard","site":"good weird","meme":"good weird",
 "tarefa de casa":"hard fun gross","faxina":"hard fun gross","cozinhar":"hard fun","dirigir":"hard fun","estudar":"hard fun","acordar cedo":"hard fun","ir ao dentista":"scary hard gross","consulta médica":"scary hard","entrevista de emprego":"scary hard",
 "primeiro encontro":"cozy scary fun","pedido de casamento":"cozy classy","casamento":"cozy classy price","jantar":"cozy classy price","almoço de família":"fun weird","reunião de família":"fun weird","churrasco":"good fun","festa de fim de ano":"fun weird classy",
 "programa de domingo":"good fun cozy","programa de sábado à noite":"good fun cozy","programa de chuva":"good cozy fun","lugar para morar":"good price classy","casa":"classy price cozy","apartamento":"classy price cozy","cômodo da casa":"good cozy",
 "vizinho":"good weird","chefe":"good scary","professor":"good scary fun","colega de trabalho":"good weird fun","amigo de infância":"good weird fun","cunhado":"good weird fun","sogro":"good weird scary","herói":"good scary classy","vilão":"scary classy weird",
 "superpoder":"good weird fun","coisa para levar numa ilha deserta":"good weird","segredo":"scary weird","mentira":"scary weird","vergonha":"scary weird gross","mico":"scary weird fun","susto":"scary weird fun","sorte":"good rare","azar":"scary rare",
 "cheiro":"good gross","barulho":"scary gross","sensação":"good gross","sonho":"good scary weird","pesadelo":"scary weird","lembrança de infância":"good weird kid","hábito":"good gross weird","mania":"gross weird fun","talento":"good rare weird",
}
HAND = ["Mole|Duro","Quente|Gelado","Lento|Rápido","Pequeno|Grande","Leve|Pesado","Liso|Áspero","Silencioso|Barulhento","Claro|Escuro","Doce|Salgado","Calmo|Agitado","Curto|Longo","Velho|Novo","Frio|Quente","Seco|Molhado","Fino|Grosso",
 "Cru|Bem passado","Vilão|Herói","Normal|Esquisito","Fofo|Assustador","Útil|Inútil","Chato de fazer|Delícia de fazer","Pior hábito|Melhor hábito","Superestimado|Subestimado","Coisa de dia|Coisa de noite","Coisa de verão|Coisa de inverno",
 "Comida de domingo|Comida de segunda","Pior nome de pessoa|Melhor nome de pessoa","Mais vergonhoso de falar em voz alta|Menos vergonhoso","Menos útil num apocalipse|Mais útil num apocalipse","Pior desculpa|Melhor desculpa","Pior em equipe|Melhor em equipe"]
FEM = {"sobremesa","bebida","fruta","verdura","comida de rua","comida de boteco","comida de avião","comida de hospital","série","novela","música","dança","matéria da escola","brincadeira de criança","festa","festa de criança","viagem","hospedagem","praia","cidade","roupa","tatuagem","tarefa de casa","faxina","consulta médica","entrevista de emprego","reunião de família","festa de fim de ano","casa","mentira","vergonha","sorte","azar","sensação","lembrança de infância","lembrança de viagem","mania","festa de fim de ano","coisa para levar numa ilha deserta","data comemorativa","refeição do dia","estação do ano","hora do dia","cor","fruta","pizza","personagem de desenho","profissão","cômodo da casa","sala","pessoa"}
# feminine forms of the adjectives that change with the noun
FEMWORD = {"barato":"barata","caro":"cara","assustador":"assustadora","esquisito":"esquisita","nojento":"nojenta","delicioso":"deliciosa","chato":"chata","divertido":"divertida","raro":"rara","romântico":"romântica"}
def agree(text, fem):
    if not fem: return text
    return " ".join(FEMWORD.get(w,w) for w in text.split(" "))
def build():
    out=list(HAND)
    for c,axes in CATS.items():
        for a in axes.split():
            if a == "kid" and "criança" in c: continue
            lo,hi=AXES[a]
            fem = c in FEM
            out.append(f"{agree(lo.format(c=c),fem)[0].upper()+agree(lo.format(c=c),fem)[1:]}|{agree(hi.format(c=c),fem)[0].upper()+agree(hi.format(c=c),fem)[1:]}")
    return out
if __name__=='__main__':
    q=build(); print(len(q))
    import random; print(*random.Random(2).sample(q,14),sep='\n')
