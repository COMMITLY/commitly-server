package com.leegeonhee.commitly.domain.retocheck

import com.leegeonhee.commitly.domain.auth.domain.repository.UserRepository
import com.leegeonhee.commitly.domain.retocheck.entity.RetoCheckEntity
import com.leegeonhee.commitly.domain.review.model.ReviewRequestBody
import com.leegeonhee.commitly.gloabl.common.BaseResponse
import com.leegeonhee.commitly.gloabl.exception.CustomException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

@Service
class RetoCheckService(
    private val retoCheckRepository: RetoCheckRepository,
    private val userRepository: UserRepository
) {

    //    C
    fun writeReview(userId: Long, review: ReviewRequestBody): BaseResponse<RetoCheckEntity> {
        val user = getUser(userId)
        return BaseResponse(
            status = 200,
            message = "굿",
            data = retoCheckRepository.save(
                RetoCheckEntity(
                    author = user,
                    message = review.message,
                )
            )
        )
    }

    //    R
    fun getMyAllReto(id: Long): BaseResponse<RetoCheckEntity> {
        val user = getUser(id)
        val usersReview = retoCheckRepository.getRetoCheckEntityByAuthorOrIdNull(user)

        return BaseResponse(
            status = 200,
            message = "에러가 안뜰거같은 기분",
            data = usersReview
        )
    }

    //    D
    fun deleteReview(userId: Long, reviewId: Long): BaseResponse<RetoCheckEntity> {
        val user = getUser(userId)
        val review = retoCheckRepository.findByIdOrNull(reviewId) ?: throw CustomException(
            status = HttpStatus.NOT_FOUND,
            message = "없"
        )
        if (review.author == user) {
            retoCheckRepository.deleteById(reviewId)
            return BaseResponse(
                status = 201,
                message = "굿",
            )
        } else {
            return BaseResponse(
                status = 404,
                message = "NOT FOUND 라는 뜻"
            )
        }
    }


    private fun getUser(userId: Long) = userRepository.findByIdOrNull(userId) ?: throw CustomException(
        status = HttpStatus.NOT_FOUND,
        message = "그런 사람 또 없습니다."
    )
}