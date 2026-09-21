# SafeLang Compiler

SafeLang é uma linguagem de programação compilada, desenvolvida no âmbito da UC de Compiladores (DETI, Universidade de Aveiro), cujo objetivo é ser o mais segura possível em tempo de compilação.

## Pré-requisitos

* JDK 17+ (`java` / `javac` no `PATH`).
* [ANTLR4](https://www.antlr.org/) 4.13+, com os comandos `antlr4` e `antlr4-build` disponíveis no `PATH` (usados pelo `build.sh` para gerar o lexer/parser a partir de `src/SafeLang.g4`).

## Build

```bash
./build.sh
```

## Compile Safelang Files

```bash
./compile.sh <file path>
```

## Run

Run the last compiled file

```bash
./run.sh
```

Or a specific file

```bash
./run.sh <file path (.java)>
```

## Clean

Remove todos os ficheiros gerados (`.class`, parser/lexer do ANTLR4, `.java` traduzidos em `examples/`), deixando apenas o código-fonte:

```bash
./clean.sh
```

## Demo — Como correr os exemplos

A pasta `examples/` contém os ficheiros `.sl` de demonstração/teste. Para cada um, o fluxo é sempre: `build.sh` (uma vez) → `compile.sh <ficheiro.sl>` → `run.sh`.

```bash
./build.sh                          # só é preciso da primeira vez (ou após alterar a gramática/compilador)
./compile.sh examples/min-01.sl
./run.sh
```

Alguns exemplos usam `read`, pelo que pedem input pelo standard input durante o `run.sh`. Pode ser fornecido interativamente ou por pipe. Exemplos testados:

```bash
# min-01.sl — pede um inteiro
./compile.sh examples/min-01.sl
echo "42" | ./run.sh

# min-02.sl — pede NMEC, nome e nota
./compile.sh examples/min-02.sl
printf "12345\nJoão\n15.5\n" | ./run.sh

# min-03.sl — usa "use physics.sl"; pede distância e tempo
./compile.sh examples/min-03.sl
printf "3.5\n1.2\n" | ./run.sh

# des-01.sl — pede um número inteiro
./compile.sh examples/des-01.sl
echo "7" | ./run.sh

# des-02.sl — usa "use grades.sl"; pede N linhas de NMEC/Nome/Nota
./compile.sh examples/des-02.sl
printf "1\nAna\n18\n2\nBruno\n12\n3\nCarla\n16\n4\nDiogo\n14\n" | ./run.sh

# des-03.sl — pede um número (procura o primeiro primo >= N)
./compile.sh examples/des-03.sl
echo "100" | ./run.sh
```

Ficheiros como `physics.sl`, `grades.sl` e `prefix.sl` não são pensados para correr sozinhos — são incluídos por outros programas através de `use "ficheiro.sl";` (processado em tempo de compilação).

## Características Mínimas

Os exemplos min-*.sl, physics.sl e grades.sl indicam algum código fonte que
tem de ser aceite (e devidamente compilado) pela linguagem a desenvolver.
A linguagem deve implementar:

* Os tipo de de dados inteiro, real e texto (ver exemplo).
* Aceitar express˜oes aritméticas standard para os tipos de dados numéricos. Aceita a
operação de concatenação de texto (operador da soma).
* Instrução de escrita no standard output.
* Instrução de leitura de texto a partir do standard input.
* Operadores de conversão entre tipos de dados (por exemplo, string(10) para converter para texto; ou integer("10") para converter para inteiro).
* Operador de formatação de strings (número minimo de colunas e, eventualmente, justificação).
2
* Instrução para definir um novo tipo de dados (que serve também para definir uma nova
dimensão). Esta definição pode ser independente (isto é definir de raiz uma nova dimensão), ou dependente. Neste último caso a definição usa uma expressão aritmética
envolvendo apenas multiplicações, divisões e/ou potências envolvendo dimensões já definidas (ver exemplos). Em qualquer um destes casos, uma dimensão funciona como um
novo tipo de dados numérico (assente em inteiros ou em reais). A definição duma dimensão envolve também a definição da sua unidade base (por exemplo, a unidade metro
para a dimensão distância), e, eventualmente, um sufixo (por exemplo, o sufixo m para
a unidade metro).
* Instrução estática (isto é, apenas com significado em tempo de compilação) para incluir
o conteúdo de outro ficheiro (semanticamente similar ao include da linguagem C)
