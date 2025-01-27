package com.leegeonhee.commitly.domain.github

import CommitInfo
import GitHubResponse
import com.leegeonhee.commitly.domain.auth.domain.model.LiteGptGetDb
import com.leegeonhee.commitly.domain.auth.domain.repository.UserRepository
import com.leegeonhee.commitly.domain.github.domain.entity.CommitInfoEntity
import com.leegeonhee.commitly.domain.github.repository.GithubRepository
import com.leegeonhee.commitly.domain.gpt.GptService
import com.leegeonhee.commitly.domain.gpt.domain.entity.GptResponseEntity
import com.leegeonhee.commitly.domain.gpt.repository.GptResponseRepository
import com.leegeonhee.commitly.gloabl.common.BaseResponse
import com.leegeonhee.commitly.gloabl.jwt.JwtUtils
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Service
class GitHubService(
    private val webClient: WebClient,
    private val githubRepository: GithubRepository,
    private val gptService: GptService,
    private val userRepository: UserRepository,
    private val gptResponseRepository: GptResponseRepository,
    private val jwtUtils: JwtUtils
) {
    fun getFromDB(name: String, date: String): BaseResponse<List<CommitInfoEntity>> {
        val commit = githubRepository.findByUserNameAndDay(name, date)
        return if (commit.isNotEmpty()) {
            BaseResponse(
                status = 200,
                message = "굿",
                data = commit
            )
        } else {
            BaseResponse(
                status = 404,
                message = "없음",
                data = emptyList()
            )
        }
    }

    fun getCommitMessages(userId: Long, date: LocalDate): BaseResponse<List<CommitInfo>> {
        val username =
            userRepository.findById(userId).get().login
        println("안녕하세혁 $date")

        val duplicationChecker = getFromDB(username, date.toString())
        if (!duplicationChecker.data.isNullOrEmpty()) {
            println("좋은거 찾음")
            return BaseResponse(
                status = 200,
                message = "잘 찾음",
                data = duplicationChecker.data.map {
                    CommitInfo(
                        repositoryName = it.repositoryName,
                        message = it.message,
                        committedDate = it.committedDate
                    )
                }
            )
        }
        println("ㅇㅇㅇ: $username")
        val fromDate = date.atStartOfDay()
        val toDate = date.atTime(LocalTime.MAX)
        val query = """
            query {
              user(login: "$username") {
                repositories(first: 100, orderBy: {field: PUSHED_AT, direction: DESC}) {
                  nodes {
                    name
                    owner {
                      login
                    }
                    defaultBranchRef {
                      target {
                        ... on Commit {
                          history(
                            first: 100,
                            since: "${fromDate.format(DateTimeFormatter.ISO_DATE_TIME)}",
                            until: "${toDate.format(DateTimeFormatter.ISO_DATE_TIME)}"
                          ) {
                            nodes {
                              message
                              committedDate
                              author {
                                user {
                                  login
                                }
                              }
                              repository {
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                organizations(first: 10) {
                  nodes {
                    login
                    repositories(first: 100, orderBy: {field: PUSHED_AT, direction: DESC}) {
                      nodes {
                        name
                        defaultBranchRef {
                          target {
                            ... on Commit {
                              history(
                                first: 100,
                                since: "${fromDate.format(DateTimeFormatter.ISO_DATE_TIME)}",
                                until: "${toDate.format(DateTimeFormatter.ISO_DATE_TIME)}"
                              ) {
                                nodes {
                                  message
                                  committedDate
                                  author {
                                    user {
                                      login
                                    }
                                  }
                                  repository {
                                    name
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val response = webClient.post()
            .bodyValue(mapOf("query" to query))
            .retrieve()
            .bodyToMono<GitHubResponse>()
            .block()


        val commitInfos = response?.data?.user?.repositories?.nodes?.flatMap { repo ->
            repo.defaultBranchRef?.target?.history?.nodes?.map { commit ->
                CommitInfo(
                    repositoryName = repo.name,
                    message = commit.message,
                    committedDate = commit.committedDate
                )
            } ?: emptyList() // 커밋이 없으면 빈 리스트로 처리
        } ?: emptyList() // repositories가 null일 경우 빈 리스트 반환

        if (commitInfos.isNotEmpty()) {
            commitInfos.forEach {
                githubRepository.save(
                    CommitInfoEntity(
                        repositoryName = it.repositoryName,
                        userName = username,
                        message = it.message,
                        committedDate = it.committedDate
                    )
                )

            }
        }
        return if (commitInfos.isEmpty()) {
            BaseResponse(
                status = 404,
                message = "커밋을 찾을 수 없습니다ddddd.",
                data = emptyList()
            )
        } else {
            BaseResponse(
                status = 200,
                message = "커밋을 성공적으로 조회했습니다.",
                data = commitInfos
            )
        }
    }


    fun generateMemoirWithGpt(userId: Long, date: LocalDate): BaseResponse<String> {
        println("Processing userId: $userId for date: $date")
//        val login = userRepository.findById(userId).get().login
        val userCommit = getCommitMessages(userId, date)

        if (userCommit.data.isNullOrEmpty()) {
            return BaseResponse(
                status = 404,
                message = "이 날의 커밋 기록이 없습니다.",
                data = null
            )
        }

        val response = gptService.askToGptRequest(userCommit.data.toString())
        val user = userRepository.findById(userId).orElseThrow {
            IllegalStateException("User not found with id: $userId")
        }

        println(response)

        gptResponseRepository.save(
            GptResponseEntity(
                user = user,
                response = response,
                responseDate = date.toString()
            )
        )

        return BaseResponse(
            status = 200,
            message = "잘옴",
            data = response
        )
    }

    fun getGptResponseFromDb(userId: Long, day: String): BaseResponse<List<LiteGptGetDb>> {
        return BaseResponse(
            status = 200,
            data = gptResponseRepository.findByUserNameAndDay(userId, day).map {
                LiteGptGetDb(
                    response = it.response,
                    responseDate = it.responseDate
                )
            },
            message = "굿"
        )
    }
}