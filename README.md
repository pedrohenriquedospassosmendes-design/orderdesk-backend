# OrderDesk Backend v59 stable

Backend Spring Boot do OrderDesk para teste local e preparação de uso real.

Esta versão reúne contas, lojas, produtos, pedidos, avaliações, curtidas, estatísticas, limite gratuito mensal e uma base protegida para futuro painel administrativo da plataforma.

## Rodar Localmente

Abra esta pasta no VS Code e rode:

```powershell
& "C:\Users\pedro\.vscode\extensions\oracle.oracle-java-25.1.0\nbcode\java\maven\bin\mvn.cmd" spring-boot:run
```

Quando o servidor iniciar na porta 8080, abra o front local.

O perfil padrão é `local`, então ele usa H2 automaticamente.

## Banco Local

O projeto ainda usa H2 apenas para teste local:

```properties
jdbc:h2:file:./data/orderdesk-v56-stable-db
```

O nome do banco local fica fixo para nao perder os dados de teste entre atualizações do backend. Nao apague a pasta `data` se quiser manter os dados locais. Trocar o nome do banco cria uma base local nova.

O backend local usa `spring.jpa.hibernate.ddl-auto=update` para preservar o H2 ja criado em testes anteriores. No local, o Flyway fica desligado por padrao para evitar travar bancos antigos criados pelo Hibernate. Nao use `create` ou `create-drop` se quiser manter os dados.

## Banco De Produção

Para produção, usar PostgreSQL, Supabase, Neon, Render PostgreSQL ou outro banco real.

Ative o perfil `prod` e configure estas variáveis de ambiente no servidor:

```properties
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://HOST:PORTA/NOME_DO_BANCO
DB_USER=usuario_do_banco
DB_PASSWORD=senha_do_banco
```

O arquivo `application-prod.properties` lê essas variáveis automaticamente:

```properties
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
```

Importante: `DATABASE_URL` deve estar no formato JDBC, começando com `jdbc:postgresql://`. Se a hospedagem entregar uma URL começando com `postgres://`, converta para o formato JDBC antes de iniciar o sistema.

Em producao, o JPA usa:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Isso evita recriar tabelas automaticamente. O schema deve ser alterado por migracoes Flyway.

## Migrações Com Flyway

O projeto usa Flyway para controlar alteracoes do banco em producao sem apagar contas, lojas, produtos e pedidos.

Pasta das migracoes:

```text
src/main/resources/db/migration
```

Arquivos criados:

- `V1__init_schema.sql`: cria o schema inicial se o banco ainda estiver vazio e adiciona colunas atuais quando possivel.
- `V2__example_add_column.sql.example`: exemplo de proxima migracao. Nao roda automaticamente porque termina com `.example`.

O Flyway cria uma tabela de controle chamada `flyway_schema_history`. Essa tabela registra quais migracoes ja rodaram. Quando o backend reinicia, ele nao roda a mesma migracao de novo.

Configuracao segura usada:

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.validate-on-migrate=true
```

Em producao, `baseline-on-migrate=true` ajuda a adotar um banco existente sem apagar dados. A versao 0 permite que a migracao V1 rode de forma defensiva em bancos existentes.

No local/teste, a configuracao padrao e:

```properties
spring.flyway.enabled=false
spring.jpa.hibernate.ddl-auto=update
```

Isso foi escolhido para o backend continuar subindo com o H2 antigo. Se quiser testar Flyway localmente em um banco H2 novo, use:

```powershell
$env:FLYWAY_ENABLED="true"
```

Depois rode o backend normalmente.

### Como Criar Nova Migração

Quando precisar adicionar um campo novo, crie um arquivo `.sql` com o proximo numero em `src/main/resources/db/migration`.

Exemplo: se no futuro precisar adicionar o campo `plan_type` na tabela `store`, crie:

```text
V2__add_plan_type_to_stores.sql
```

Conteudo:

```sql
ALTER TABLE store ADD COLUMN IF NOT EXISTS plan_type VARCHAR(50) DEFAULT 'FREE';
```

Regras:

- nunca editar uma migracao que ja rodou em producao;
- criar sempre uma nova migracao para a proxima mudanca;
- testar primeiro no H2/local;
- fazer backup do PostgreSQL antes de atualizar o site em producao;
- nunca usar `create-drop` em producao;
- nunca trocar o nome do banco sem entender que isso aponta para outra base.

### Sobre Itens Do Pedido

Hoje o sistema guarda os itens do pedido em `customer_order.items_json`. A migracao inicial tambem cria uma tabela `order_item` vazia para uma normalizacao futura, mas o backend atual ainda usa `items_json`.

## Segurança Atual

- Senhas ficam salvas com BCrypt, nao em texto puro.
- Login gera token de sessão aleatório.
- Logout limpa o token salvo no servidor.
- Email invalido é recusado no cadastro e no perfil.
- Conta de cliente nao cria loja.
- Conta lojista cria no maximo 4 lojas.
- O backend valida dono da loja para editar/excluir lojas.
- O backend valida dono da loja para criar/editar/excluir produtos.
- O backend valida dono da loja para ver pedidos e alterar status.
- Curtida duplicada é bloqueada por loja e visitante.
- Avaliação duplicada é bloqueada por loja e visitante.
- Excluir conta pede senha e remove lojas, produtos, pedidos, curtidas e avaliações ligadas à conta.
- O tipo da conta nao pode mais ser alterado pelo perfil comum.

Ainda falta para produção: JWT ou sessão mais robusta, expiração formal de token, confirmação de email, recuperação de senha, auditoria e proteção contra abuso.

## Entidades/Tabelas

- `UserAccount`: conta, email, senha criptografada, token, tipo de conta, papel de plataforma, avatar e localização.
- `Store`: loja, dono, contato, localização, status, horários, pedido mínimo, cobrança e limite gratuito.
- `Product`: produto, loja, preço, imagem, categoria, disponibilidade, destaque e promoção.
- `CustomerOrder`: pedido, cliente, entrega/retirada, itens, subtotal, taxa, total, status e data.
- `StoreLike`: curtida única por loja e visitante.
- `StoreReview`: avaliação única por loja e visitante.

## Endpoints Existentes

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/auth/logout`
- `PUT /api/auth/profile`
- `DELETE /api/auth/account`
- `GET /api/stores`
- `GET /api/stores/mine/{ownerId}`
- `GET /api/stores/slug/{slug}`
- `POST /api/stores`
- `PUT /api/stores/{id}`
- `DELETE /api/stores/{id}`
- `PATCH /api/stores/{id}/active`
- `GET /api/stores/{id}/billing`
- `PATCH /api/stores/{id}/billing/test-usage`
- `POST /api/stores/{id}/like`
- `GET /api/stores/{storeId}/reviews`
- `POST /api/stores/{storeId}/reviews`
- `GET /api/stores/{storeId}/products`
- `GET /api/stores/slug/{slug}/products`
- `POST /api/stores/{storeId}/products`
- `PUT /api/stores/{storeId}/products/{productId}`
- `PATCH /api/products/{productId}/availability`
- `DELETE /api/products/{productId}`
- `POST /api/stores/slug/{slug}/orders`
- `GET /api/stores/{storeId}/orders`
- `PATCH /api/orders/{orderId}/status`
- `PATCH /api/orders/{orderId}/cancel`
- `GET /api/stats/owner/{ownerId}`
- `GET /api/stats/customer`
- `GET /api/platform-admin/summary`

## Admin Da Plataforma

Existe uma base simples em `GET /api/platform-admin/summary?token=...`.

Ela só responde para conta com `platformRole = ADMIN` no banco. Por enquanto nao existe tela completa. Funções futuras planejadas:

- ver todas as contas;
- ver todas as lojas;
- bloquear/desbloquear loja;
- remover produto suspeito;
- ver denúncias;
- ver lojas perto do limite;
- ver lojas bloqueadas;
- ver estatísticas gerais;
- controlar planos.

## Cobrança E Limite Gratuito

Modelo atual:

- plano grátis com 30 pedidos recebidos por mês;
- aviso perto do limite;
- bloqueio de novos pedidos ao atingir limite;
- dono continua acessando painel e pedidos antigos;
- dono pode confirmar vendas antigas;
- cliente vê apenas mensagem amigável quando a loja nao recebe pedidos.

Mensagens esperadas:

- perto do limite: "Você está perto do limite gratuito: 25/30 pedidos usados neste mês.";
- no limite: "Limite gratuito atingido. Regularize para continuar recebendo pedidos.";
- cliente: "Esta loja não está recebendo pedidos no momento.".

Pagamento real ainda nao foi integrado.

## Estatísticas

Para lojista:

- pedidos recebidos;
- vendas confirmadas;
- pedidos cancelados;
- valor de pedidos recebidos;
- valor vendido confirmado;
- ticket médio confirmado;
- produtos cadastrados;
- lojas criadas;
- pedidos por status;
- pedidos usados no plano gratuito;
- pedidos restantes no mês.

Regra: valor vendido conta apenas status `confirmado`, `preparando`, `saiu_entrega` e `finalizado`.

Para cliente:

- pedidos feitos;
- valor total confirmado;
- status dos pedidos;
- últimos pedidos.

## Teste Completo

1. Criar conta de loja com email válido.
2. Tentar criar conta com email inválido.
3. Entrar na conta.
4. Recarregar a página do front e conferir sessão mantida.
5. Sair da conta e conferir que precisa entrar de novo.
6. Criar loja.
7. Editar loja.
8. Excluir loja de teste.
9. Criar produto.
10. Editar produto.
11. Pausar produto e confirmar que nao aparece publicamente.
12. Excluir produto de teste.
13. Abrir loja pública pelo slug.
14. Fazer pedido com entrega.
15. Fazer pedido com retirada.
16. Ver pedido no painel do dono.
17. Confirmar venda.
18. Conferir estatísticas.
19. Cancelar pedido pelo cliente quando ainda estiver permitido.
20. Alterar loja para fechada/pausada e tentar pedir.
21. Simular limite gratuito com `/billing/test-usage`.
22. Conferir aviso perto do limite.
23. Conferir bloqueio ao atingir limite.
24. Confirmar que pedidos antigos continuam visíveis.

## Faltando Para Produção

- PostgreSQL ou outro banco real;
- JWT/sessão com expiração e renovação controlada;
- confirmação de email;
- recuperação de senha;
- painel admin completo;
- denúncias/moderação;
- pagamento real;
- armazenamento de imagens em nuvem;
- termos de uso e política de privacidade;
- logs e monitoramento;
- testes automatizados.

## Segurança da v70

Esta versão reduz o risco de alteração indevida de lojas, produtos, pedidos e estatísticas.
As rotas de lojista agora validam o `token` da sessão no backend e não confiam mais apenas no `ownerId` enviado pelo navegador.

Rotas sensíveis como criar/editar/excluir loja, criar/editar/excluir produto, listar pedidos, alterar status de pedido e ver estatísticas do lojista exigem token válido.
## Admin do dono v72

O painel interno do dono fica no front em `admin-dono.html`.
No backend, use a variavel de ambiente `PLATFORM_ADMIN_PASSWORD` no Render para definir a senha real do painel.
Em teste local, a senha padrao e `admin123`.

Rotas usadas:
- `POST /api/platform-admin/login`
- `GET /api/platform-admin/summary?token=...`
- `GET /api/platform-admin/accounts?token=...`
- `GET /api/platform-admin/stores?token=...`
- `GET /api/platform-admin/orders?token=...`