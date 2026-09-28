package speiderhytta.audit

/** Representative utdrag fra virkelige GitHub-responser hentet 2026-09-22. */
object GithubPayloads {
    val commentsResponse = """
        [
          {
            "url": "https://api.github.com/repos/navikt/team-helved/issues/comments/5677480690",
            "html_url": "https://github.com/navikt/team-helved/issues/633#issuecomment-5677480690",
            "issue_url": "https://api.github.com/repos/navikt/team-helved/issues/633",
            "id": 5677480690,
            "node_id": "IC_example",
            "user": {
              "login": "developer-three",
              "id": 100000003,
              "node_id": "USER_example",
              "avatar_url": "https://avatars.githubusercontent.com/u/100000003?v=4",
              "url": "https://api.github.com/users/developer-three",
              "html_url": "https://github.com/developer-three",
              "type": "User",
              "site_admin": false
            },
            "created_at": "2026-09-15T08:56:31Z",
            "updated_at": "2026-09-15T08:59:14Z",
            "body": "Vanlig kommentar fra et teammedlem.",
            "author_association": "MEMBER",
            "pin": null,
            "reactions": {
              "url": "https://api.github.com/repos/navikt/team-helved/issues/comments/5677480690/reactions",
              "total_count": 0,
              "+1": 0,
              "-1": 0,
              "laugh": 0,
              "hooray": 0,
              "confused": 0,
              "heart": 0,
              "rocket": 0,
              "eyes": 0
            },
            "performed_via_github_app": null,
            "minimized": null
          }
        ]
    """.trimIndent()


        val commitsResponse = """
            [
              {
                "sha":"657319ffa817aed1d3e0ac3d4e6e39dff10911db",
                "commit":{
                  "author":{"name":"Developer One","email":"100000001+developer-one@users.noreply.github.com","date":"2026-09-22T10:18:02Z"},
                  "committer":{"name":"Developer One","email":"100000001+developer-one@users.noreply.github.com","date":"2026-09-22T10:18:03Z"},
                  "message":"#511 Oppdater bigquery dep\n\nSigned-off-by: Developer One <100000001+developer-one@users.noreply.github.com>",
                  "verification":{"verified":false,"reason":"unsigned","signature":null,"payload":null,"verified_at":null}
                },
                "author":{"login":"developer-one","id":100000001},
                "committer":{"login":"developer-one","id":100000001},
                "parents":[{"sha":"f4c12809cc056e9792076484d2d064f135fe01fd"}]
              },
              {
                "sha":"5f418c072bc03c1b784afef034af278d2626c44f",
                "commit":{
                  "author":{"date":"2026-09-22T07:46:50Z"},
                  "committer":{"date":"2026-09-22T07:46:50Z"},
                  "message":"Merge pull request #342 from navikt/dependabot/gradle/plugin.serialization-2.4.20\n\n#511: Bump plugin.serialization from 2.4.10 to 2.4.20",
                  "verification":{"verified":true,"reason":"valid"}
                },
                "author":{"login":"github-actions[bot]"},
                "committer":{"login":"web-flow"},
                "parents":[{"sha":"fe2c41b9"},{"sha":"eac8f154"}]
              },
              {
                "sha":"0fae4100f8aa7f3242fdfc58680d76cbc9ef9657",
                "commit":{
                  "author":{"name":"Developer Two","email":"developer.two@example.invalid","date":"2026-09-15T12:06:44Z"},
                  "committer":{"name":"Developer Two","email":"developer.two@example.invalid","date":"2026-09-15T12:06:44Z"},
                  "message":"dryrun topic for valp og historisk #636",
                  "verification":{"verified":true,"reason":"valid"}
                },
                "author":{"login":"developer-two"},
                "committer":{"login":"developer-two"},
                "parents":[{"sha":"116226600f4cdb059507bbefbcdc1b6cb1a8c859"}]
              }
            ]
        """.trimIndent()

        val workflowResponse = """
            {"total_count":1,"workflow_runs":[{
              "id":34967033382,"name":"abetal","head_branch":"main",
              "head_sha":"0fae4100f8aa7f3242fdfc58680d76cbc9ef9657",
              "path":".github/workflows/abetal.yml","event":"push","status":"completed","conclusion":"success",
              "created_at":"2026-09-15T12:07:16Z","updated_at":"2026-09-15T12:12:35Z",
              "run_started_at":"2026-09-15T12:07:16Z","html_url":"https://github.com/navikt/helved-utbetaling/actions/runs/34967033382",
              "run_attempt":1,"actor":{"login":"developer-two"},"triggering_actor":{"login":"developer-two"}
            }]}
        """.trimIndent()

        val jobsResponse = """
            {"total_count":4,"jobs":[
              {"id":104373822667,"run_id":34967033382,"run_attempt":1,"head_sha":"0fae4100f8aa7f3242fdfc58680d76cbc9ef9657","name":"package","status":"completed","conclusion":"success","created_at":"2026-09-15T12:07:16Z","started_at":"2026-09-15T12:07:18Z","completed_at":"2026-09-15T12:09:19Z","steps":[{"name":"Run nais/docker-build-push@v0","status":"completed","conclusion":"success","number":9},{"name":"Run actions/cache/save@v6.1.0","status":"completed","conclusion":"skipped","number":11}]},
              {"id":104373822915,"run_id":34967033382,"run_attempt":1,"head_sha":"0fae4100f8aa7f3242fdfc58680d76cbc9ef9657","name":"test","status":"completed","conclusion":"success","created_at":"2026-09-15T12:07:16Z","started_at":"2026-09-15T12:07:18Z","completed_at":"2026-09-15T12:08:39Z","steps":[{"name":"Run ./gradlew apps:abetal:test --continue --parallel","status":"completed","conclusion":"success","number":5}]},
              {"id":104374446596,"run_id":34967033382,"run_attempt":1,"head_sha":"0fae4100f8aa7f3242fdfc58680d76cbc9ef9657","name":"deploy-dev","status":"completed","conclusion":"success","created_at":"2026-09-15T12:09:19Z","started_at":"2026-09-15T12:09:21Z","completed_at":"2026-09-15T12:12:19Z","steps":[{"name":"Run nais/deploy/actions/deploy@v2","status":"completed","conclusion":"success","number":5}]},
              {"id":104374446598,"run_id":34967033382,"run_attempt":1,"head_sha":"0fae4100f8aa7f3242fdfc58680d76cbc9ef9657","name":"deploy-prod","status":"completed","conclusion":"success","created_at":"2026-09-15T12:09:19Z","started_at":"2026-09-15T12:09:21Z","completed_at":"2026-09-15T12:12:35Z","steps":[{"name":"Run nais/deploy/actions/deploy@v2","status":"completed","conclusion":"success","number":5}]}
            ]}
        """.trimIndent()

        val rulesetsResponse = """
            [{"id":15706978,"name":"require issue id","target":"branch","source_type":"Repository",
              "source":"navikt/helved-utbetaling","enforcement":"active",
              "_links":{"self":{"href":"https://api.github.com/repos/navikt/helved-utbetaling/rulesets/15706978"}}}]
        """.trimIndent()
}
